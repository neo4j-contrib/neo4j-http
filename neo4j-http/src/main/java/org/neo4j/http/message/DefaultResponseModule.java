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
import java.time.Duration;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.value.LossyCoercion;
import org.neo4j.driver.summary.InputPosition;
import org.neo4j.driver.summary.Notification;
import org.neo4j.driver.summary.SummaryCounters;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.TypeSystem;
import org.neo4j.driver.util.Pair;
import org.neo4j.http.app.Views;
import org.neo4j.http.db.EagerResult;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * A module that understands both <a href="https://neo4j.com/docs/http-api/current/actions/query-format/">HTTP Query format</a> and
 * <a href="https://neo4j.com/docs/java-manual/current/cypher-workflow/#java-driver-type-mapping">supported value types</a>.
 *
 * @author Michael J. Simons
 */
final class DefaultResponseModule extends SimpleModule {

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
	DefaultResponseModule(TypeSystem typeSystem) {

		this.typeSystem = typeSystem;
		this.simpleTypes = Set.of(typeSystem.NULL(), typeSystem.BOOLEAN(), typeSystem.STRING(), typeSystem.INTEGER(), typeSystem.FLOAT());

		this.addSerializer(Record.class, new RecordSerializer());
		this.addSerializer(Value.class, new ValueSerializer());
		this.addSerializer(EagerResult.ResultData.class, new ResultDataSerializer());
		this.addSerializer(SummaryCounters.class, new SummaryCountersSerializer());

		this.setMixInAnnotation(Notification.class, NotificationMixIn.class);
		this.setMixInAnnotation(InputPosition.class, InputPositionMixIn.class);
		this.setMixInAnnotation(Neo4jException.class, Neo4jExceptionMixIn.class);
	}

	private boolean hasSimpleType(Value value) {
		return value == null || simpleTypes.stream().anyMatch(value::hasType);
	}

	private interface NotificationMixIn {

		@JsonProperty
		String code();

		@JsonProperty
		String severity();

		@JsonProperty
		String title();

		@JsonProperty
		String description();

		@JsonProperty
		org.neo4j.driver.summary.InputPosition position();
	}

	private interface InputPositionMixIn {

		@JsonProperty
		int column();

		@JsonProperty
		int line();

		@JsonProperty
		int offset();
	}

	@JsonIncludeProperties({"code", "message"})
	private interface Neo4jExceptionMixIn {

		@JsonProperty
		String code();
	}

	private static class SummaryCountersSerializer extends StdSerializer<SummaryCounters> {

		@Serial
		private static final long serialVersionUID = -4434233555324168878L;

		SummaryCountersSerializer() {
			super(SummaryCounters.class);
		}

		@Override
		public void serialize(SummaryCounters value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			if (value == null) {
				return;
			}
			gen.writeStartObject();
			gen.writeBooleanField("contains_updates", value.containsUpdates());
			gen.writeNumberField("nodes_created", value.nodesCreated());
			gen.writeNumberField("nodes_deleted", value.nodesDeleted());
			gen.writeNumberField("properties_set", value.propertiesSet());
			gen.writeNumberField("relationships_created", value.relationshipsCreated());
			gen.writeNumberField("relationship_deleted", value.relationshipsDeleted());
			gen.writeNumberField("labels_added", value.labelsAdded());
			gen.writeNumberField("labels_removed", value.labelsRemoved());
			gen.writeNumberField("indexes_added", value.indexesAdded());
			gen.writeNumberField("indexes_removed", value.indexesRemoved());
			gen.writeNumberField("constraints_added", value.constraintsAdded());
			gen.writeNumberField("constraints_removed", value.constraintsRemoved());
			gen.writeBooleanField("contains_system_updates", value.containsSystemUpdates());
			gen.writeNumberField("system_updates", value.systemUpdates());
			gen.writeEndObject();
		}
	}

	private static class ResultDataSerializer extends StdSerializer<EagerResult.ResultData> {

		@Serial
		private static final long serialVersionUID = 8801941034117580880L;

		ResultDataSerializer() {
			super(EagerResult.ResultData.class);
		}

		@Override
		public void serialize(EagerResult.ResultData value, JsonGenerator gen, SerializerProvider provider) throws IOException {

			if (value.records() == null && value.graph() == null && (value.rest() == null || value.rest().isEmpty())) {
				return;
			}

			gen.writeStartObject();
			if (value.records() != null) {
				var recordSerializer = provider.findValueSerializer(Record.class);
				recordSerializer.serialize(value.records(), gen, provider);
			}
			if (value.graph() != null) {
				var recordSerializer = provider.findValueSerializer(Value.class);
				gen.writeFieldName("graph");
				recordSerializer.serialize(Values.value(value.graph()), gen, provider);
			}

			if (value.rest() != null && !value.rest().isEmpty()) {
				gen.writeObjectField("rest", value.rest());
			}

			gen.writeEndObject();
		}
	}


	private final class RecordSerializer extends StdSerializer<Record> {

		@Serial
		private static final long serialVersionUID = 8594507829627684699L;

		RecordSerializer() {
			super(Record.class);
		}

		@Override
		public void serialize(Record value, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {

			var valueSerializer = serializerProvider.findValueSerializer(Value.class);
			if (serializerProvider.getActiveView() == Views.NEO4J_44_DEFAULT.class) {
				boolean needsObject = !json.getOutputContext().inObject();
				if (needsObject) {
					json.writeStartObject();
				}
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
				if (needsObject) {
					json.writeEndObject();
				}
			} else {
				json.writeStartObject();
				for (Pair<String, Value> pair : value.fields()) {
					json.writeFieldName(pair.key());
					valueSerializer.serialize(pair.value(), json, serializerProvider);
				}
				json.writeEndObject();
			}
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

		private final Map<Type, CypherTypes> typeToNames;

		ValueSerializer() {
			super(Value.class);
			this.typeToNames = Map.of(
				typeSystem.BYTES(), CypherTypes.ByteArray,
				typeSystem.DATE(), CypherTypes.Date,
				typeSystem.TIME(), CypherTypes.Time,
				typeSystem.LOCAL_TIME(), CypherTypes.LocalTime,
				typeSystem.DATE_TIME(), CypherTypes.DateTime,
				typeSystem.LOCAL_DATE_TIME(), CypherTypes.LocalDateTime
			);
		}

		@Override
		public void serialize(Value value, JsonGenerator json, SerializerProvider serializers) throws IOException {

			if (value.hasType(typeSystem.LIST())) {
				json.writeStartArray();
				for (Value element : value.values()) {
					serialize(element, json, serializers);
				}
				json.writeEndArray();
			} else if (value.hasType(typeSystem.MAP()) && !(value.hasType(typeSystem.NODE()) || value.hasType(typeSystem.RELATIONSHIP()))) {
				json.writeStartObject();
				for (String key : value.keys()) {
					json.writeFieldName(key);
					serialize(value.get(key), json, serializers);
				}
				json.writeEndObject();
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

			if (typeToNames.containsKey(value.type())) {
				var cypherType = typeToNames.get(value.type());
				json.writeStartObject();
				json.writeStringField(Fieldnames.CYPHER_TYPE, cypherType.getValue());
				json.writeStringField(Fieldnames.CYPHER_VALUE, cypherType.getWriter().apply(value));
				json.writeEndObject();
			} else if (value.hasType(typeSystem.POINT())) {
				renderPoint(value, json, true);
			} else if (value.hasType(typeSystem.NODE())) {
				var node = value.asNode();
				json.writeStartObject();
				json.writeStringField(Fieldnames.CYPHER_TYPE, CypherTypes.Node.getValue());
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

	/**
	 * This adapter maps a Driver or embedded based {@link TemporalAmount} to a valid Java temporal amount. It tries to be
	 * as specific as possible: If the amount can be reliable mapped to a {@link Period}, it returns a period. If only
	 * fields are present that are no estimated time unites, then it returns a {@link Duration}. <br>
	 * <br>
	 * In cases a user has used Cypher and its <code>duration()</code> function, i.e. like so
	 * <code>CREATE (s:SomeTime {isoPeriod: duration('P13Y370M45DT25H120M')}) RETURN s</code> a duration object has been
	 * created that cannot be represented by either a {@link Period} or {@link Duration}. The user has to map it to a plain
	 * {@link TemporalAmount} in these cases. <br>
	 * The Java Driver uses a <code>org.neo4j.driver.v1.types.IsoDuration</code>, embedded uses
	 * <code>org.neo4j.values.storable.DurationValue</code> for representing a temporal amount, but in the end, they can be
	 * treated the same. However, be aware that the temporal amount returned in that case may not be equal to the other one,
	 * only represents the same amount after normalization.
	 *
	 * @author Michael J. Simons
	 */
	// Welcome to its 3rd installment, after the first appearance in Neo4j-OGM, than Spring Data Neo4j 6 now the HTTP PoC
	static final class TemporalAmountAdapter implements Function<TemporalAmount, TemporalAmount> {

		private static final int PERIOD_MASK = 0b11100;
		private static final int DURATION_MASK = 0b00011;
		private static final TemporalUnit[] SUPPORTED_UNITS = {ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS,
			ChronoUnit.SECONDS, ChronoUnit.NANOS};

		private static final short FIELD_YEAR = 0;
		private static final short FIELD_MONTH = 1;
		private static final short FIELD_DAY = 2;
		private static final short FIELD_SECONDS = 3;
		private static final short FIELD_NANOS = 4;

		private static final BiFunction<TemporalAmount, TemporalUnit, Integer> TEMPORAL_UNIT_EXTRACTOR = (d, u) -> {
			if (!d.getUnits().contains(u)) {
				return 0;
			}
			return Math.toIntExact(d.get(u));
		};

		@Override
		public TemporalAmount apply(TemporalAmount internalTemporalAmountRepresentation) {

			int[] values = new int[SUPPORTED_UNITS.length];
			int type = 0;
			for (int i = 0; i < SUPPORTED_UNITS.length; ++i) {
				values[i] = TEMPORAL_UNIT_EXTRACTOR.apply(internalTemporalAmountRepresentation, SUPPORTED_UNITS[i]);
				type |= (values[i] == 0) ? 0 : (0b10000 >> i);
			}

			boolean couldBePeriod = couldBePeriod(type);
			boolean couldBeDuration = couldBeDuration(type);

			if (couldBePeriod && !couldBeDuration) {
				return Period.of(values[FIELD_YEAR], values[FIELD_MONTH], values[FIELD_DAY]).normalized();
			} else if (couldBeDuration && !couldBePeriod) {
				return Duration.ofSeconds(values[FIELD_SECONDS]).plusNanos(values[FIELD_NANOS]);
			} else {
				return internalTemporalAmountRepresentation;
			}
		}

		private static boolean couldBePeriod(int type) {
			return (PERIOD_MASK & type) > 0;
		}

		private static boolean couldBeDuration(int type) {
			return (DURATION_MASK & type) > 0;
		}
	}
}
