package org.neo4j.http.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CypherRequestParsingTest {

	private static final ObjectMapper objectMapper = new ObjectMapper();


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
				Arguments.of("\"LocalDate\"", "\"2022-10-18\"", LocalDate.of(2022, 10, 18))

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
						"parameters": {"value": {"type": %s, "value": %s}}
					}
				]
			}""".formatted(typeName, value);

		var cypherRequest = objectMapper.readValue(payload, CypherRequest.class);

		assertThat(cypherRequest.statements().get(0).parameters().get("value")).isEqualTo(expected);

	}



}