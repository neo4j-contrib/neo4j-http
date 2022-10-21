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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Test to ensure message parsing works correct.
 */
class CypherRequestParsingTest {

	private static final ObjectMapper objectMapper = new ObjectMapper();


	@BeforeAll
	static void setupMapper() {
		objectMapper.registerModule(new ParameterTypesModule());

	}

	@Test
	void marshalCypherRequestFromPayload() throws JsonProcessingException {
		var payload = """
					{
						"statements": [
							{
								"statement": "MATCH (n) RETURN n",
								"parameters": {"name": "Neo4j-HTTP-Proxy"}
							}
						]
				}""";

		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(cypherRequest.statements()).hasSize(1);
		assertThat(cypherRequest.statements().get(0).statement()).isEqualTo("MATCH (n) RETURN n");
		assertThat(cypherRequest.statements().get(0).parameters()).isEqualTo(Map.of("name", "Neo4j-HTTP-Proxy"));
	}

	@Test
	void marshalMultipleCypherRequestFromPayload() throws JsonProcessingException {
		var payload = """
					{
						"statements": [
							{
								"statement": "MATCH (n) RETURN n",
								"parameters": {"name": "Neo4j-HTTP-Proxy"}
							},
							{
								"statement": "CREATE (n:Node{name:$name})",
								"parameters": {"name": "Test"}
							}
						]
				}""";
		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(cypherRequest.statements()).hasSize(2);
		assertThat(cypherRequest.statements().get(0).statement()).isEqualTo("MATCH (n) RETURN n");
		assertThat(cypherRequest.statements().get(0).parameters()).isEqualTo(Map.of("name", "Neo4j-HTTP-Proxy"));
		assertThat(cypherRequest.statements().get(1).statement()).isEqualTo("CREATE (n:Node{name:$name})");
		assertThat(cypherRequest.statements().get(1).parameters()).isEqualTo(Map.of("name", "Test"));
	}

	private static Stream<Arguments> simpleTypesParams() {
		return Stream.of(
				Arguments.of("Boolean", true, true),
				Arguments.of("Boolean", false, false),
				Arguments.of("Integer", 4711, 4711),
				Arguments.of("Long", 2345345984934892L, 2345345984934892L),
				Arguments.of("Double", 3.123d, 3.123d),
				Arguments.of("Float", 2.3111f, 2.3111d),
				Arguments.of("String", "\"a string\"", "a string")
		);
	}

	@ParameterizedTest
	@MethodSource("simpleTypesParams")
	void marshalSimpleTypes(String typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "%s",
							"parameters": {"value":%s}
						}
					]
				}""".formatted(typeName, value);

		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(cypherRequest.statements().get(0).parameters().get("value")).isEqualTo(expected);


	}

	@ParameterizedTest
	@MethodSource("simpleTypesParams")
	void marshalSimpleTypesAsList(String typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "%s",
							"parameters": {"value":[%s]}
						}
					]
				}""".formatted(typeName, value);

		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(cypherRequest.statements().get(0).parameters().get("value")).isEqualTo(List.of(expected));


	}

	private static Stream<Arguments> complexTypesParams() {
		return Stream.of(
				Arguments.of("\"LocalDate\"", "\"2022-10-18\"", LocalDate.of(2022, 10, 18)),
				Arguments.of("\"OffsetTime\"", "\"13:37:11+02:00\"", OffsetTime.of(LocalTime.of(13, 37, 11), ZoneOffset.ofHours(2))),
				Arguments.of("\"LocalTime\"", "\"13:37:11\"", LocalTime.of(13, 37, 11)),
				Arguments.of("\"ZonedDateTime\"", "\"2022-10-18T13:37:11+02:00[Europe/Paris]\"", ZonedDateTime.of(LocalDate.of(2022, 10, 18), LocalTime.of(13, 37, 11), ZoneId.of("Europe/Paris"))),
				Arguments.of("\"LocalDateTime\"", "\"2022-10-18T13:37:11\"", LocalDateTime.of(LocalDate.of(2022, 10, 18), LocalTime.of(13, 37, 11))),
				Arguments.of("\"Duration\"", "\"PT23H21M\"", Duration.ofHours(23).plusMinutes(21)),
				Arguments.of("\"Period\"", "\"P20D\"", Period.ofDays(20)),
				Arguments.of("\"Point\"", "\"SRID=4979;POINT(12.994823 55.612191 2)\"", new PointParameter(4979, 12.994823, 55.612191, 2)),
				Arguments.of("\"Point\"", "\"SRID=4326;POINT(12.994823 55.612191)\"", new PointParameter(4326, 12.994823, 55.612191, 0)),
				Arguments.of("\"Byte[]\"", "\"00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10\"", new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
		);
	}

	@ParameterizedTest
	@MethodSource("complexTypesParams")
	void marshalComplexTypes(String typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": {"type": %s, "value": %s}, "someSimpleTypeValue": "Hello"}
						}
					]
				}""".formatted(typeName, value);

		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(cypherRequest.statements().get(0).parameters().get("value")).isEqualTo(expected);

	}

	@ParameterizedTest
	@MethodSource("complexTypesParams")
	void marshalComplexTypesInList(String typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": [{"type": %s, "value": %s}], "someOtherContainer": ["hellO"]}
						}
					]
				}""".formatted(typeName, value);

		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(((List<?>) cypherRequest.statements().get(0).parameters().get("value")).get(0)).isEqualTo(expected);

	}

	@ParameterizedTest
	@MethodSource("complexTypesParams")
	void marshalComplexTypesInMap(String typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": {"nestedObject": {"type": %s, "value": %s}}, "someOtherContainer": ["hellO"]}
						}
					]
				}""".formatted(typeName, value);

		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(((Map<?, ?>) cypherRequest.statements().get(0).parameters().get("value")).get("nestedObject")).isEqualTo(expected);

	}

	@ParameterizedTest
	@MethodSource("complexTypesParams")
	void marshalComplexTypesInListOfMap(String typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": [{"nestedObject": {"type": %s, "value": %s}}], "someOtherContainer": ["hellO"]}
						}
					]
				}""".formatted(typeName, value);

		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(((Map<?, ?>) ((List<?>) cypherRequest.statements().get(0).parameters().get("value")).get(0)).get("nestedObject")).isEqualTo(expected);

	}

	@Test
	void throwExceptionOnUnknownComplexType() {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": {"type":"Unknown", "value":"something"}}
						}
					]
				}""";

		assertThatExceptionOfType(JsonMappingException.class).isThrownBy(() -> objectMapper.readValue(payload, CypherRequest.class))
				.havingRootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.withMessageStartingWith("Cannot convert Unknown into a known type. Convertible types are ");

	}

	@Test
	void throwExceptionOnUnsupportedComplexTypeValue() {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": {"type":"LocalDate", "value":true}}
						}
					]
				}""";

		assertThatExceptionOfType(JsonMappingException.class).isThrownBy(() -> objectMapper.readValue(payload, CypherRequest.class))
				.havingRootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.withMessage("Value true (type BOOLEAN) for type LocalDate has to be String-based.");

	}


}
