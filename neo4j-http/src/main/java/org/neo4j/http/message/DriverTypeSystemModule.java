/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.http.message;

import java.io.IOException;
import java.io.Serial;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.value.LossyCoercion;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.TypeSystem;
import org.neo4j.driver.util.Pair;
import org.neo4j.http.app.Views;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson module to deal with Java driver types.
 *
 * @author Michael J. Simons
 */
public final class DriverTypeSystemModule extends SimpleModule {

	@Serial
	private static final long serialVersionUID = -6600328341718439212L;

	/**
	 * New type system delegating to the drivers {@link TypeSystem}.
	 *
	 * @param typeSystem Retrieved from the driver
	 */
	public DriverTypeSystemModule(TypeSystem typeSystem) {
		this.addSerializer(Record.class, new RecordSerializer(typeSystem));
		this.addSerializer(Value.class, new ValueSerializer(typeSystem));
	}

	static final class RecordSerializer extends StdSerializer<Record> {


		@Serial
		private static final long serialVersionUID = 8594507829627684699L;

		private final TypeSystem typeSystem;

		RecordSerializer(TypeSystem typeSystem) {
			super(Record.class);
			this.typeSystem = typeSystem;
		}

		@SuppressWarnings("deprecation")
		@Override
		public void serialize(Record value, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {

			var valueSerializer = serializerProvider.findValueSerializer(Value.class);

			json.writeStartObject();
			if (serializerProvider.getActiveView() == Views.Default.class) {
				json.writeArrayFieldStart("row");
				for (Value column : value.values()) {
					valueSerializer.serialize(column, json, serializerProvider);
				}
				json.writeEndArray();

				json.writeArrayFieldStart("meta");
				for (Value column : value.values()) {
					if (column.hasType(typeSystem.NODE()) || column.hasType(typeSystem.RELATIONSHIP())) {
						json.writeStartObject();
						json.writeNumberField("id", column.asEntity().id());
						json.writeStringField("type", column.type().name().toLowerCase(Locale.ROOT));
						json.writeEndObject();
					} else {
						json.writeNull();
					}
				}
				json.writeEndArray();
			} else {
				for (Pair<String, Value> pair : value.fields()) {
					json.writeFieldName(pair.key());
					valueSerializer.serialize(pair.value(), json, serializerProvider);
				}
			}
			json.writeEndObject();
		}
	}


	static final class ValueSerializer extends StdSerializer<Value> {

		@Serial
		private static final long serialVersionUID = -5914605165093400044L;

		private final TypeSystem typeSystem;

		ValueSerializer(TypeSystem typeSystem) {
			super(Value.class);
			this.typeSystem = typeSystem;
		}

		@Override
		public void serialize(Value value, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {

			if (value == null || value.isNull()) {
				json.writeNull();
			} else if (value.hasType(typeSystem.BOOLEAN())) {
				json.writeBoolean(value.asBoolean());
			} else if (value.hasType(typeSystem.STRING())) {
				json.writeString(value.asString());
			} else if (value.hasType(typeSystem.INTEGER())) {
				json.writeNumber(value.asLong());
			} else if (value.hasType(typeSystem.FLOAT())) {
				try {
					json.writeNumber(value.asFloat());
				} catch (LossyCoercion e) {
					json.writeNumber(value.asDouble());
				}
			} else if (value.hasType(typeSystem.DATE())) {
				json.writeStartObject();
				json.writeStringField(Fieldnames.CYPHER_TYPE, CypherTypenames.Date.getValue());
				json.writeStringField(Fieldnames.CYPHER_VALUE, DateTimeFormatter.ISO_LOCAL_DATE.format(value.asLocalDate()));
				json.writeEndObject();
			} else if (value.hasType(typeSystem.NODE())) {

				var node = value.asNode();
				if (serializerProvider.getActiveView() == Views.Default.class) {
					writeEntityProperties(node, json, serializerProvider);
				} else {
					json.writeStartObject();
					json.writeStringField(Fieldnames.CYPHER_TYPE, CypherTypenames.Node.getValue());
					json.writeFieldName(Fieldnames.CYPHER_VALUE);
					json.writeStartObject();

					json.writeArrayFieldStart(Fieldnames.LABELS);
					for (String label : node.labels()) {
						json.writeString(label);
					}
					json.writeEndArray();

					json.writeFieldName(Fieldnames.PROPERTIES);
					writeEntityProperties(node, json, serializerProvider);
					json.writeEndObject();
					json.writeEndObject();
				}
			} else if (value.hasType(typeSystem.LIST())) {
				json.writeStartArray();
				for (Value element : value.values()) {
					serialize(element, json, serializerProvider);
				}
				json.writeEndArray();
			} else if (value.hasType(typeSystem.RELATIONSHIP())) {

				var relationship = value.asRelationship();
				if (serializerProvider.getActiveView() == Views.Default.class) {
					writeEntityProperties(relationship, json, serializerProvider);
				} else {
					// TODO
				}
			}
		}

		private void writeEntityProperties(Entity node, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {

			json.writeStartObject();
			for (String property : node.keys()) {
				json.writeFieldName(property);
				serialize(node.get(property), json, serializerProvider);
			}
			json.writeEndObject();
		}

	}
}
