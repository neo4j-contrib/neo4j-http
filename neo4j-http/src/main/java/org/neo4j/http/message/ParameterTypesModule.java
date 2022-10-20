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
 * A module that understands the <a href="https://neo4j.com/docs/java-reference/current/extending-neo4j/values-and-types/">supported value types</a>
 * and contributes a {@link JsonObjectDeserializer} for all excluding Nodes, Relationships and Paths.
 *
 * @author Gerrit Meier
 */
public class ParameterTypesModule extends SimpleModule {

	@Serial
	private static final long serialVersionUID = 4711L;

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
					JsonNode customType = tree.get("type");
					if (customType != null) {
						String customTypeName = customType.asText();
						if (!ParameterConverter.canConvert(customTypeName)) {
							throw new IllegalArgumentException("Cannot convert %s into a known type. Convertible types are %s".formatted(customTypeName, ParameterConverter.CONVERTERS.keySet()));
						}
						String typeValue = tree.get("value").asText();
						return ParameterConverter.CONVERTER.apply(customTypeName, typeValue);
					}

					return jsonParser.getCodec().treeToValue(tree, Map.class);
				}
			}
			throw new IllegalArgumentException("Cannot parse %s as a valid parameter type".formatted(tree));

		}
	}

	private static class ParameterConverter {
		private static final Map<String, Function<Object, Object>> CONVERTERS = Map.of(
				"LocalDate", value -> LocalDate.parse((String) value, DateTimeFormatter.ISO_LOCAL_DATE),
				"OffsetTime", value -> OffsetTime.parse((String) value, DateTimeFormatter.ISO_OFFSET_TIME),
				"LocalTime", value -> LocalTime.parse((String) value, DateTimeFormatter.ISO_LOCAL_TIME),
				"ZonedDateTime", value -> ZonedDateTime.parse((String) value, DateTimeFormatter.ISO_ZONED_DATE_TIME),
				"LocalDateTime", value -> LocalDateTime.parse((String) value, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
				"Duration", value -> Duration.parse((String) value),
				"Period", value -> Period.parse((String) value),
				"Point", value -> PointParameter.of((String) value)

		);

		private final static BiFunction<String, Object, Object> CONVERTER = (typeName, value) -> CONVERTERS.get(typeName).apply(value);

		private static boolean canConvert(String type) {
			return CONVERTERS.keySet().contains(type);
		}
	}
}
