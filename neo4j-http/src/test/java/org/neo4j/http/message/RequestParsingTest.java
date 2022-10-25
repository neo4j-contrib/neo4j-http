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

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.types.InternalTypeSystem;
import org.neo4j.http.db.AnnotatedQuery;
import org.neo4j.http.config.JacksonConfig;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

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
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test to ensure message parsing works correct.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequestParsingTest {

	private final ObjectMapper objectMapper;

	private final Driver driver;

	RequestParsingTest() {

		driver = mock(Driver.class);
		when(driver.defaultTypeSystem()).thenReturn(InternalTypeSystem.TYPE_SYSTEM);

		var jacksonObjectMapperBuilder = new Jackson2ObjectMapperBuilder();
		new JacksonConfig().objectMapperBuilderCustomizer(driver).customize(jacksonObjectMapperBuilder);

		this.objectMapper = jacksonObjectMapperBuilder.build();
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

		var cypherRequest = objectMapper.readValue(payload, AnnotatedQuery.Container.class);

		assertThat(cypherRequest.value()).hasSize(1);
		assertThat(cypherRequest.value().get(0).value().text()).isEqualTo("MATCH (n) RETURN n");
		assertThat(cypherRequest.value().get(0).value().parameters().asMap(Function.identity()))
			.containsEntry("name", Values.value("Neo4j-HTTP-Proxy"));
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
		var cypherRequest = objectMapper.readValue(payload, AnnotatedQuery.Container.class);

		assertThat(cypherRequest.value()).hasSize(2);
		assertThat(cypherRequest.value()).element(0).extracting(AnnotatedQuery::value).satisfies(query -> {
			assertThat(query.text()).isEqualTo("MATCH (n) RETURN n");
			assertThat(query.parameters().asMap(Function.identity())).containsEntry("name", Values.value("Neo4j-HTTP-Proxy"));
		});
		assertThat(cypherRequest.value()).element(1).extracting(AnnotatedQuery::value).satisfies(query -> {
			assertThat(query.text()).isEqualTo("CREATE (n:Node{name:$name})");
			assertThat(query.parameters().asMap(Function.identity())).containsEntry("name", Values.value("Test"));
		});
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

		var cypherRequest = objectMapper.readValue(payload, AnnotatedQuery.Container.class);
		assertThat(cypherRequest.value().get(0).value().parameters().get("value")).isEqualTo(Values.value(expected));
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

		var cypherRequest = objectMapper.readValue(payload, AnnotatedQuery.Container.class);
		assertThat(cypherRequest.value().get(0).value().parameters().get("value")).isEqualTo(Values.value(List.of(expected)));
	}

	private static Stream<Arguments> complexTypesParams() {
		return Stream.of(
				Arguments.of(CypherTypenames.Date, "2022-10-18", LocalDate.of(2022, 10, 18)),
				Arguments.of(CypherTypenames.Time, "13:37:11+02:00", OffsetTime.of(LocalTime.of(13, 37, 11), ZoneOffset.ofHours(2))),
				Arguments.of(CypherTypenames.LocalTime, "13:37:11", LocalTime.of(13, 37, 11)),
				Arguments.of(CypherTypenames.DateTime, "2022-10-18T13:37:11+02:00[Europe/Paris]", ZonedDateTime.of(LocalDate.of(2022, 10, 18), LocalTime.of(13, 37, 11), ZoneId.of("Europe/Paris"))),
				Arguments.of(CypherTypenames.LocalDateTime, "2022-10-18T13:37:11", LocalDateTime.of(LocalDate.of(2022, 10, 18), LocalTime.of(13, 37, 11))),
				Arguments.of(CypherTypenames.Duration, "PT23H21M", Duration.ofHours(23).plusMinutes(21)),
				Arguments.of(CypherTypenames.Period, "P20D", Period.ofDays(20)),
				Arguments.of(CypherTypenames.Point, "SRID=4979;POINT(12.994823 55.612191 2)", Values.point(4979, 12.994823, 55.612191, 2)),
				Arguments.of(CypherTypenames.Point, "SRID=4326;POINT(12.994823 55.612191)", Values.point(4326, 12.994823, 55.612191)),
				Arguments.of(CypherTypenames.ByteArray, "00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10", new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
		);
	}

	@ParameterizedTest
	@MethodSource("complexTypesParams")
	void marshalComplexTypes(CypherTypenames typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": {"$type": "%s", "_value": "%s"}, "someSimpleTypeValue": "Hello"}
						}
					]
				}""".formatted(typeName.getValue(), value);

		var cypherRequest = objectMapper.readValue(payload, AnnotatedQuery.Container.class);
		assertThat(cypherRequest.value().get(0).value().parameters().get("value")).isEqualTo(Values.value(expected));
	}

	@ParameterizedTest
	@MethodSource("complexTypesParams")
	void marshalComplexTypesInList(CypherTypenames typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": [{"$type": "%s", "_value": "%s"}], "someOtherContainer": ["hellO"]}
						}
					]
				}""".formatted(typeName.getValue(), value);

		var cypherRequest = objectMapper.readValue(payload, AnnotatedQuery.Container.class);
		assertThat(cypherRequest.value().get(0).value().parameters().get("value")).isEqualTo(Values.value(List.of(expected)));
	}

	@ParameterizedTest
	@MethodSource("complexTypesParams")
	void marshalComplexTypesInMap(CypherTypenames typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": {"nestedObject": {"$type": "%s", "_value": "%s"}}, "someOtherContainer": ["hellO"]}
						}
					]
				}""".formatted(typeName.getValue(), value);

		var cypherRequest = objectMapper.readValue(payload, AnnotatedQuery.Container.class);
		assertThat(cypherRequest.value().get(0).value().parameters().get("value").get("nestedObject")).isEqualTo(Values.value(expected));
	}

	@ParameterizedTest
	@MethodSource("complexTypesParams")
	void marshalComplexTypesInListOfMap(CypherTypenames typeName, Object value, Object expected) throws JsonProcessingException {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": [{"nestedObject": {"$type": "%s", "_value": "%s"}}], "someOtherContainer": ["hellO"]}
						}
					]
				}""".formatted(typeName.getValue(), value);

		var cypherRequest = objectMapper.readValue(payload, AnnotatedQuery.Container.class);
		assertThat(cypherRequest.value().get(0).value().parameters().get("value"))
			.extracting(v -> v.asList(Function.identity()))
			.asInstanceOf(InstanceOfAssertFactories.list(Value.class))
			.element(0)
			.extracting(v -> v.get("nestedObject"))
			.isEqualTo(Values.value(expected));
	}

	@Test
	void throwExceptionOnUnknownComplexType() {
		var payload = """
				{
					"statements": [
						{
							"statement": "complex types",
							"parameters": {"value": {"$type":"Unknown", "_value":"something"}}
						}
					]
				}""";

		assertThatExceptionOfType(JsonMappingException.class).isThrownBy(() -> objectMapper.readValue(payload, AnnotatedQuery.Container.class))
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
							"parameters": {"value": {"$type":"Date", "_value":true}}
						}
					]
				}""";

		assertThatExceptionOfType(JsonMappingException.class).isThrownBy(() -> objectMapper.readValue(payload, AnnotatedQuery.Container.class))
				.havingRootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.withMessage("Value true (type BOOLEAN) for type Date has to be String-based.");

	}
}
