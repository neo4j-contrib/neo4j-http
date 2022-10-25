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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import org.neo4j.driver.Query;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Point;
import org.neo4j.http.db.AnnotatedQuery;
import org.springframework.boot.jackson.JsonObjectDeserializer;

import java.io.IOException;
import java.io.Serial;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A module that understands both <a href="https://neo4j.com/docs/http-api/current/actions/query-format/">HTTP Query format</a> and
 * <a href="https://neo4j.com/docs/java-manual/current/cypher-workflow/#java-driver-type-mapping">supported value types</a> for
 * deserializing requests.
 *
 * @author Gerrit Meier
 * @author Michael J. Simons
 */
final class DefaultRequestFormatModule extends SimpleModule {

	@Serial
	private static final long serialVersionUID = 6857894267001773659L;

	/**
	 * Default instance
	 */
	DefaultRequestFormatModule() {
		this.addDeserializer(Value.class, new ParameterDeserializer());
		this.addDeserializer(Query.class, new QueryDeserializer());

		this.setMixInAnnotation(AnnotatedQuery.Container.class, AnnotatedQueryContainerMixIn.class);
		this.addDeserializer(AnnotatedQuery.class, new AnnotatedQueryDeserializer());
	}

	private static abstract class AnnotatedQueryContainerMixIn {

		@JsonCreator
		AnnotatedQueryContainerMixIn(@JsonProperty("statements") List<AnnotatedQuery> value) {
		}
	}

	/**
	 * Not done via MixIn so that the query text can be normalized.
	 */
	private static final class QueryDeserializer extends JsonObjectDeserializer<Query> {

		String normalizeQuery(String query) {
			return Optional.ofNullable(query).map(String::trim).filter(Predicate.not(String::isBlank)).orElseThrow();
		}

		@Override
		protected Query deserializeObject(JsonParser jsonParser, DeserializationContext context, ObjectCodec codec, JsonNode tree) throws IOException {

			var text = normalizeQuery(tree.get("statement").asText());
			var parameters = tree.has("parameters") ? codec.readValue(codec.treeAsTokens(tree.get("parameters")), Value.class) : Values.EmptyMap;
			return new Query(text, parameters);
		}
	}

	/**
	 * Not possible via a mixin, as statement are on the same level of the parameter map and not individually addressable.
	 */
	private static class AnnotatedQueryDeserializer extends JsonObjectDeserializer<AnnotatedQuery> {

		@Override
		protected AnnotatedQuery deserializeObject(JsonParser jsonParser, DeserializationContext context, ObjectCodec codec, JsonNode tree) throws IOException {

			var query = codec.treeToValue(tree, Query.class);
			var resultDataContents = codec.treeToValue(tree.get("resultDataContents"), AnnotatedQuery.ResultFormat[].class);
			var includeStats = tree.has("includeStats") && tree.get("includeStats").asBoolean();
			return new AnnotatedQuery(query, includeStats, resultDataContents);
		}
	}

	/**
	 * Generic {@link Value} deserializer
	 */
	private static class ParameterDeserializer extends JsonObjectDeserializer<Value> {

		@Override
		protected Value deserializeObject(JsonParser jsonParser, DeserializationContext context, ObjectCodec codec, JsonNode tree) throws IOException {

			if (tree.isValueNode()) {
				if (tree.isTextual()) {
					return Values.value(tree.asText());
				}
				if (tree.isFloatingPointNumber()) {
					return Values.value(tree.asDouble());
				}
				if (tree.isBoolean()) {
					return Values.value(tree.asBoolean());
				}
				if (tree.isInt()) {
					return Values.value(tree.asInt());
				}
				if (tree.isNumber()) {
					return Values.value(tree.asLong());
				}
			}
			if (tree.isContainerNode()) {
				if (tree.getNodeType().equals(JsonNodeType.ARRAY)) {
					return Values.value(codec.readValue(codec.treeAsTokens(tree), new TypeReference<List<Value>>() {
					}));
				}
				if (tree.getNodeType().equals(JsonNodeType.OBJECT)) {
					JsonNode customType = tree.get(Fieldnames.CYPHER_TYPE);
					if (customType != null) {
						String customTypeName = customType.asText();
						if (!canConvert(customTypeName)) {
							throw new IllegalArgumentException("Cannot convert %s into a known type. Convertible types are %s".formatted(customTypeName, CONVERTERS.keySet()));
						}
						ValueNode typeValue = tree.get(Fieldnames.CYPHER_VALUE).require();
						var apply = CONVERTER.apply(customTypeName, typeValue);
						return Values.value(apply);
					}
					return Values.value(codec.readValue(codec.treeAsTokens(tree), new TypeReference<Map<String, Value>>() {
					}));
				}
			}
			throw new IllegalArgumentException("Cannot parse %s as a valid parameter type".formatted(tree));
		}
	}

	private final static Pattern WKT_PATTERN = Pattern.compile("SRID=(\\d+);\\s*POINT\\(\\s*(\\S+)\\s+(\\S+)\\s*(\\S?)\\)");

	/**
	 * Pragmatic parsing of the Neo4j Java Driver's {@link Point} class
	 * This method does not check if the parameters align with the given coordinate system or if the coordinate system code is valid.
	 *
	 * @param input WKT representation of a point
	 */
	private static Point parsePoint(String input) {
		var matcher = WKT_PATTERN.matcher(input);

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Illegal pattern"); //todo add right pattern syntax in exception message
		}

		var srid = Integer.parseInt(matcher.group(1));
		var x = Double.parseDouble(matcher.group(2));
		var y = Double.parseDouble(matcher.group(3));
		var z = matcher.group(4);
		if (z != null && !z.isBlank()) {
			return Values.point(srid, x, y, Double.parseDouble(z)).asPoint();
		} else {
			return Values.point(srid, x, y).asPoint();
		}
	}

	private static final Map<String, Function<String, Object>> CONVERTERS = Map.of(
		CypherTypenames.Date.getValue(), value -> LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE),
		CypherTypenames.Time.getValue(), value -> OffsetTime.parse(value, DateTimeFormatter.ISO_OFFSET_TIME),
		CypherTypenames.LocalTime.getValue(), value -> LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME),
		CypherTypenames.DateTime.getValue(), value -> ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME),
		CypherTypenames.LocalDateTime.getValue(), value -> LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
		CypherTypenames.Duration.getValue(), Duration::parse,
		CypherTypenames.Period.getValue(), Period::parse,
		CypherTypenames.Point.getValue(), DefaultRequestFormatModule::parsePoint,
		CypherTypenames.ByteArray.getValue(), DefaultRequestFormatModule::parseByteString
	);

	private final static BiFunction<String, ValueNode, Object> CONVERTER = (typeName, value) -> {
		if (value instanceof TextNode textNode) {
			return CONVERTERS.get(typeName).apply(textNode.asText());
		}
		throw new IllegalArgumentException("Value %s (type %s) for type %s has to be String-based.".formatted(value, value.getNodeType(), typeName));
	};

	private static boolean canConvert(String type) {
		return CONVERTERS.containsKey(type);
	}

	private static byte[] parseByteString(String rawInput) {
		var input = rawInput.replaceAll("\s*", "");
		int inputLength = input.length();
		var result = new byte[inputLength / 2];

		for (int i = 0; i < inputLength; i += 2) {
			result[i / 2] = (byte) ((Character.digit(input.charAt(i), 16) << 4)
				+ Character.digit(input.charAt(i + 1), 16));
		}

		return result;
	}
}
