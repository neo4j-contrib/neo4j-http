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
import org.neo4j.http.db.AnnotatedQuery;
import org.springframework.boot.jackson.JsonObjectDeserializer;

import java.io.IOException;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
							var convertibleTypes = Arrays.stream(CypherTypes.values()).filter(ct -> ct.getReader() != null).collect(Collectors.toSet());
							throw new IllegalArgumentException("Cannot convert %s into a known type. Convertible types are %s".formatted(customTypeName, convertibleTypes));
						}
						ValueNode typeValue = tree.get(Fieldnames.CYPHER_VALUE).require();
						return CONVERTER.apply(customTypeName, typeValue);
					}
					return Values.value(codec.readValue(codec.treeAsTokens(tree), new TypeReference<Map<String, Value>>() {
					}));
				}
			}
			throw new IllegalArgumentException("Cannot parse %s as a valid parameter type".formatted(tree));
		}
	}

	private final static BiFunction<String, ValueNode, Value> CONVERTER = (typeName, value) -> {
		if (value instanceof TextNode textNode) {
			return CypherTypes.byNameOrValue(typeName).getReader().apply(textNode.asText());
		}
		throw new IllegalArgumentException("Value %s (type %s) for type %s has to be String-based.".formatted(value, value.getNodeType(), typeName));
	};

	private static boolean canConvert(String type) {
		try {
			var cypherType = CypherTypes.byNameOrValue(type);
			return cypherType.getReader() != null;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
