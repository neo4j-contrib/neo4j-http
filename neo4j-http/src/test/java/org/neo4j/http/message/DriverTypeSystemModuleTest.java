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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.types.InternalTypeSystem;
import org.neo4j.driver.internal.value.NodeValue;
import org.neo4j.driver.types.Node;
import org.neo4j.http.app.Views;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DriverTypeSystemModuleTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	DriverTypeSystemModuleTest() {
		this.objectMapper.registerModule(new DriverTypeSystemModule(InternalTypeSystem.TYPE_SYSTEM));
	}

	static NodeValue mockNode() {
		var node = mock(Node.class);
		when(node.keys()).thenReturn(List.of("s", "n"));
		when(node.get("s")).thenReturn(Values.value("foo"));
		when(node.get("n")).thenReturn(Values.value(4711));
		when(node.labels()).thenReturn(List.of("L1", "L2", "$L3"));
		return new NodeValue(node);
	}

	static Stream<Arguments> shouldSerializeValues() {
		return Stream.of(
			Arguments.of(Values.NULL, "null"),
			Arguments.of(Values.value("foo"), "\"foo\""),
			Arguments.of(Values.value(42), "42"),
			Arguments.of(Values.value(42.23), "42.23"),
			Arguments.of(Values.value((float) 42.23), "42.23"),
			Arguments.of(Values.value(false), "false"),
			Arguments.of(Values.value(LocalDate.of(2022, 10, 21)), "{\"$type\":\"Date\",\"_value\":\"2022-10-21\"}"),
			Arguments.of(mockNode(), "{\"$type\":\"Node\",\"_value\":{\"_labels\":[\"L1\",\"L2\",\"$L3\"],\"_props\":{\"s\":\"foo\",\"n\":4711}}}")
		);
	}

	@ParameterizedTest
	@MethodSource
	void shouldSerializeValues(Value value, String expected) throws JsonProcessingException {

		var data = Map.of("var", value);
		var json = objectMapper
			.writeValueAsString(data);
		assertThat(json).isEqualTo(String.format("{\"var\":%s}", expected.strip()));
	}

	static Stream<Arguments> shouldSerializeValuesWithDefaultView() {
		return Stream.of(
			Arguments.of(mockNode(), "{\"s\":\"foo\",\"n\":4711}")
		);
	}

	@ParameterizedTest
	@MethodSource
	void shouldSerializeValuesWithDefaultView(Value value, String expected) throws JsonProcessingException {

		var data = Map.of("var", value);
		var json = objectMapper
			.writerWithView(Views.Default.class)
			.writeValueAsString(data);
		assertThat(json).isEqualTo(String.format("{\"var\":%s}", expected.strip()));
	}
}
