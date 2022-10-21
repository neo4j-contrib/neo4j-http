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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A module that understands the <a href="https://neo4j.com/docs/java-manual/current/cypher-workflow/#java-driver-type-mapping">supported value types</a>
 * and contributes a {@link JsonObjectDeserializer} for all excluding Nodes, Relationships and Paths.
 *
 * @author Gerrit Meier
 */
public class ParameterTypesModule extends SimpleModule {

	@Serial
	private static final long serialVersionUID = 6857894267001773659L;

	public ParameterTypesModule() {
		this.addDeserializer(Object.class, new ParameterDeserializer());
	}

	private static class ParameterDeserializer extends JsonObjectDeserializer<Object> {

		@Override
		protected Object deserializeObject(JsonParser jsonParser, DeserializationContext context, ObjectCodec codec, JsonNode tree) throws IOException {


			if (tree.isValueNode()) {
				if (tree.isTextual()) {
					return tree.asText();
				}
				if (tree.isFloatingPointNumber()) {
					return tree.asDouble();
				}
				if (tree.isBoolean()) {
					return tree.asBoolean();
				}
				if (tree.isInt()) {
					return tree.asInt();
				}
				if (tree.isNumber()) {
					return tree.asLong();
				}
			}
			if (tree.isContainerNode()) {
				if (tree.getNodeType().equals(JsonNodeType.ARRAY)) {
					return jsonParser.getCodec().treeToValue(tree, List.class);
				}
				if (tree.getNodeType().equals(JsonNodeType.OBJECT)) {
					JsonNode customType = tree.get(Fieldnames.CYPHER_TYPE);
					if (customType != null) {
						String customTypeName = customType.asText();
						if (!ParameterConverter.canConvert(customTypeName)) {
							throw new IllegalArgumentException("Cannot convert %s into a known type. Convertible types are %s".formatted(customTypeName, ParameterConverter.CONVERTERS.keySet()));
						}
						ValueNode typeValue = tree.get(Fieldnames.CYPHER_VALUE).require();
						return ParameterConverter.CONVERTER.apply(customTypeName, typeValue);
					}

					return jsonParser.getCodec().treeToValue(tree, Map.class);
				}
			}
			throw new IllegalArgumentException("Cannot parse %s as a valid parameter type".formatted(tree));

		}
	}

	private static class ParameterConverter {
		private static final Map<String, Function<String, Object>> CONVERTERS = Map.of(
			CypherTypenames.Date.getValue(), value -> LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE),
			CypherTypenames.Time.getValue(), value -> OffsetTime.parse(value, DateTimeFormatter.ISO_OFFSET_TIME),
			CypherTypenames.LocalTime.getValue(), value -> LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME),
			CypherTypenames.DateTime.getValue(), value -> ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME),
			CypherTypenames.LocalDateTime.getValue(), value -> LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
			CypherTypenames.Duration.getValue(), Duration::parse,
			CypherTypenames.Period.getValue(), Period::parse,
			CypherTypenames.Point.getValue(), PointParameter::of,
			CypherTypenames.ByteArray.getValue(), ParameterConverter::parseByteString
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
}
