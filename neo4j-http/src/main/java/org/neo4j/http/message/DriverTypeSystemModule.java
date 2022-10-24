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
import java.util.Map;
import java.util.Set;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.value.LossyCoercion;
import org.neo4j.driver.summary.InputPosition;
import org.neo4j.driver.summary.Notification;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.TypeSystem;
import org.neo4j.driver.util.Pair;
import org.neo4j.http.app.Views;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
	 * Needed for mostly all value serializers.
	 */
	private final TypeSystem typeSystem;

	/**
	 * A set of type that will be serialized as plain JSON values.
	 */
	private final Set<Type> simpleTypes;

	/**
	 * New type system delegating to the drivers {@link TypeSystem}.
	 *
	 * @param typeSystem Retrieved from the driver
	 */
	public DriverTypeSystemModule(TypeSystem typeSystem) {

		this.typeSystem = typeSystem;
		this.simpleTypes = Set.of(typeSystem.NULL(), typeSystem.BOOLEAN(), typeSystem.STRING(), typeSystem.INTEGER(), typeSystem.FLOAT());
		this.addSerializer(Record.class, new RecordSerializer());
		this.addSerializer(Value.class, new ValueSerializer());
		this.setMixInAnnotation(Notification.class, NotificationMixin.class);
		this.setMixInAnnotation(InputPosition.class, InputPositionMixIn.class);
		this.setMixInAnnotation(Neo4jException.class, Neo4jExceptionMixIn.class);
	}

	private boolean hasSimpleType(Value value) {
		return value == null || simpleTypes.stream().anyMatch(value::hasType);
	}

	interface NotificationMixin {

		@JsonProperty
		String code();

		@JsonProperty
		String severity();

		@JsonProperty
		String title();

		@JsonProperty
		String description();

		@JsonProperty
		InputPosition position();
	}

	interface InputPositionMixIn {

		@JsonProperty
		int column();

		@JsonProperty
		int line();

		@JsonProperty
		int offset();
	}

	@JsonIncludeProperties({"code", "message"})
	interface Neo4jExceptionMixIn {

		@JsonProperty
		String code();
	}

	final class RecordSerializer extends StdSerializer<Record> {

		@Serial
		private static final long serialVersionUID = 8594507829627684699L;

		RecordSerializer() {
			super(Record.class);
		}

		@Override
		public void serialize(Record value, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {

			var valueSerializer = serializerProvider.findValueSerializer(Value.class);

			json.writeStartObject();
			if (serializerProvider.getActiveView() == Views.NEO4J_44_DEFAULT.class) {
				json.writeArrayFieldStart("row");
				for (Value column : value.values()) {
					valueSerializer.serialize(column, json, serializerProvider);
				}
				json.writeEndArray();

				json.writeArrayFieldStart("meta");
				for (Value column : value.values()) {
					if (column.hasType(typeSystem.NODE()) || column.hasType(typeSystem.RELATIONSHIP())) {
						writeMetaEntity(column.asEntity(), json);
					} else if (column.hasType(typeSystem.PATH())) {
						json.writeStartArray();
						var path = column.asPath();
						for (Path.Segment element : path) {
							writeMetaEntity(element.start(), json);
							writeMetaEntity(element.relationship(), json);
						}
						writeMetaEntity(path.end(), json);
						json.writeEndArray();
					} else if (hasSimpleType(column) || column.hasType(typeSystem.LIST()) || column.hasType(typeSystem.MAP())) {
						json.writeNull();
					} else {
						json.writeStartObject();
						json.writeStringField("type", column.type().name().toLowerCase(Locale.ROOT).replace("_", ""));
						json.writeEndObject();
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

		@SuppressWarnings("deprecation")
		private static void writeMetaEntity(Entity column, JsonGenerator json) throws IOException {
			json.writeStartObject();
			json.writeNumberField("id", column.id());

			String type;
			if (column instanceof Node) {
				type = "node";
			} else if (column instanceof Relationship) {
				type = "relationship";
			} else {
				throw new IllegalArgumentException("Unsupported entity " + column.getClass());
			}
			json.writeStringField("type", type);
			json.writeEndObject();
		}
	}

	final class ValueSerializer extends StdSerializer<Value> {

		@Serial
		private static final long serialVersionUID = -5914605165093400044L;

		private static final Map<Integer, String> SRID_MAPPING = Map.of(
			7203, "cartesian",
			9157, "cartesian-3d",
			4326, "wgs-84",
			4979, "wgs-84-3d"
		);
		private static final Map<Integer, String> FORMAT_MAPPING = Map.of(
			7203, "http://spatialreference.org/ref/sr-org/%d/ogcwkt/",
			9157, "http://spatialreference.org/ref/sr-org/%d/ogcwkt/",
			4326, "http://spatialreference.org/ref/epsg/%d/ogcwkt/",
			4979, "http://spatialreference.org/ref/epsg/%d/ogcwkt/"
		);

		private final TemporalAmountAdapter temporalAmountAdapter = new TemporalAmountAdapter();

		ValueSerializer() {
			super(Value.class);
		}

		@Override
		public void serialize(Value value, JsonGenerator json, SerializerProvider serializers) throws IOException {

			if (value.hasType(typeSystem.LIST())) {
				json.writeStartArray();
				for (Value element : value.values()) {
					serialize(element, json, serializers);
				}
				json.writeEndArray();
			} else if (hasSimpleType(value)) {
				renderSimpleValue(value, json);
			} else if (serializers.getActiveView() == Views.NEO4J_44_DEFAULT.class) {
				renderOldFormat(value, json, serializers);
			} else {
				renderNewFormat(value, json, serializers);
			}
		}

		private void renderOldFormat(Value value, JsonGenerator json, SerializerProvider serializers) throws IOException {

			if (value.hasType(typeSystem.DATE())) {
				json.writeObject(value.asLocalDate());
			} else if (value.hasType(typeSystem.DATE_TIME())) {
				json.writeString(value.asZonedDateTime().format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
			} else if (value.hasType(typeSystem.DURATION())) {
				json.writeObject(temporalAmountAdapter.apply(value.asIsoDuration()));
			} else if (value.hasType(typeSystem.LOCAL_DATE_TIME())) {
				json.writeObject(value.asLocalDateTime());
			} else if (value.hasType(typeSystem.LOCAL_TIME())) {
				json.writeObject(value.asLocalTime());
			} else if (value.hasType(typeSystem.NODE()) || value.hasType(typeSystem.RELATIONSHIP())) {
				var node = value.asEntity();
				writeEntityProperties(node, json, serializers);
			} else if (value.hasType(typeSystem.PATH())) {
				json.writeStartArray();
				var path = value.asPath();
				for (Path.Segment element : path) {
					writeEntityProperties(element.start(), json, serializers);
					writeEntityProperties(element.relationship(), json, serializers);
				}
				writeEntityProperties(path.end(), json, serializers);
				json.writeEndArray();
			} else if (value.hasType(typeSystem.POINT())) {
				renderPoint(value, json, false);
			} else if (value.hasType(typeSystem.TIME())) {
				json.writeObject(value.asOffsetTime());
			} else {
				throw new UnsupportedOperationException("Type " + value.type().name() + " is not supported as a column value");
			}
		}

		private void renderNewFormat(Value value, JsonGenerator json, SerializerProvider serializers) throws IOException {

			if (value.hasType(typeSystem.DATE())) {
				json.writeStartObject();
				json.writeStringField(Fieldnames.CYPHER_TYPE, CypherTypenames.Date.getValue());
				json.writeStringField(Fieldnames.CYPHER_VALUE, DateTimeFormatter.ISO_LOCAL_DATE.format(value.asLocalDate()));
				json.writeEndObject();
			} else if (value.hasType(typeSystem.POINT())) {
				renderPoint(value, json, true);
			} else if (value.hasType(typeSystem.NODE())) {
				var node = value.asNode();
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
				writeEntityProperties(node, json, serializers);
				json.writeEndObject();
				json.writeEndObject();
			} else {
				throw new UnsupportedOperationException("Type " + value.type().name() + " is not supported as a column value");
			}
		}

		private void renderPoint(Value value, JsonGenerator json, boolean newFormat) throws IOException {

			var point = value.asPoint();
			json.writeStartObject();
			json.writeStringField(newFormat ? Fieldnames.CYPHER_TYPE : "type", "Point");
			if (newFormat) {
				json.writeFieldName(Fieldnames.CYPHER_VALUE);
				json.writeStartObject();
			}

			json.writeArrayFieldStart("coordinates");
			json.writeNumber(point.x());
			json.writeNumber(point.y());
			if (!Double.isNaN(point.z())) {
				json.writeNumber(point.z());
			}
			json.writeEndArray();
			json.writeObjectFieldStart("crs");
			json.writeNumberField("srid", point.srid());
			json.writeStringField("name", SRID_MAPPING.getOrDefault(point.srid(), "n/a"));
			json.writeStringField("type", "link");
			if (FORMAT_MAPPING.containsKey(point.srid())) {
				json.writeObjectFieldStart("properties");
				json.writeStringField("href", FORMAT_MAPPING.get(point.srid()).formatted(point.srid()));
				json.writeStringField("type", "ogcwkt");
				json.writeEndObject();
			}
			json.writeEndObject();

			if (newFormat) {
				json.writeEndObject();
			}
			json.writeEndObject();
		}

		private void renderSimpleValue(Value value, JsonGenerator json) throws IOException {

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
			}
		}

		private void writeEntityProperties(Entity node, JsonGenerator json, SerializerProvider serializers) throws IOException {

			json.writeStartObject();
			for (String property : node.keys()) {
				json.writeFieldName(property);
				serialize(node.get(property), json, serializers);
			}
			json.writeEndObject();
		}

	}
}
