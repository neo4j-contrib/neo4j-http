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

import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.value.LossyCoercion;
import org.neo4j.driver.internal.types.InternalTypeSystem;
import org.neo4j.driver.types.TypeSystem;
import org.neo4j.http.app.Views;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

final class DriverTypeSystemModule extends SimpleModule {

	@Serial
	private static final long serialVersionUID = -6600328341718439212L;

	DriverTypeSystemModule() {
		this.addSerializer(Value.class, new NonEntityValueSerializer(InternalTypeSystem.TYPE_SYSTEM));
	}


	static final class NonEntityValueSerializer extends StdSerializer<Value> {

		@Serial
		private static final long serialVersionUID = -5914605165093400044L;

		private final TypeSystem typeSystem;


		NonEntityValueSerializer(TypeSystem typeSystem) {
			super(Value.class);
			this.typeSystem = typeSystem;
		}

		@Override
		public void serialize(Value value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {

			if (value == null || value.isNull()) {
				jsonGenerator.writeNull();
			} else if (typeSystem.BOOLEAN().isTypeOf(value)) {
				jsonGenerator.writeBoolean(value.asBoolean());
			} else if (typeSystem.STRING().isTypeOf(value)) {
				jsonGenerator.writeString(value.asString());
			} else if (typeSystem.INTEGER().isTypeOf(value)) {
				jsonGenerator.writeNumber(value.asLong());
			} else if (typeSystem.FLOAT().isTypeOf(value)) {
				try {
					jsonGenerator.writeNumber(value.asFloat());
				} catch (LossyCoercion e) {
					jsonGenerator.writeNumber(value.asDouble());
				}
			} else if (typeSystem.DATE().isTypeOf(value)) {
				jsonGenerator.writeStartObject();
				jsonGenerator.writeStringField(Fieldnames.CYPHER_TYPE, CypherTypenames.Date.getValue());
				jsonGenerator.writeStringField(Fieldnames.CYPHER_VALUE, DateTimeFormatter.ISO_LOCAL_DATE.format(value.asLocalDate()));
				jsonGenerator.writeEndObject();
			} else if (typeSystem.NODE().isTypeOf(value)) {

				var node = value.asNode();
				jsonGenerator.writeStartObject();


				if (serializerProvider.getActiveView() == Views.Default.class) {
// here goes the standard "old" format later
				} else {

					jsonGenerator.writeStringField(Fieldnames.CYPHER_TYPE, CypherTypenames.Node.getValue());
					jsonGenerator.writeFieldName(Fieldnames.CYPHER_VALUE);
					jsonGenerator.writeStartObject();

					jsonGenerator.writeFieldName(Fieldnames.LABELS);
					jsonGenerator.writeNull();
				}
				jsonGenerator.writeFieldName(Fieldnames.PROPERTIES);
				jsonGenerator.writeStartObject();
				for (String property : node.keys()) {
					jsonGenerator.writeFieldName(property);
					serialize(node.get(property), jsonGenerator, serializerProvider);
				}
				jsonGenerator.writeEndObject();


				if (serializerProvider.getActiveView() == Views.Default.class) {
					jsonGenerator.writeEndObject();
				}
				jsonGenerator.writeEndObject();
			}
		}

	}
}
