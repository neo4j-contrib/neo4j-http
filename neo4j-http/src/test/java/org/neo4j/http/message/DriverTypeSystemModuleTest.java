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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.types.InternalTypeSystem;
import org.neo4j.driver.internal.value.NodeValue;
import org.neo4j.driver.types.Node;
import org.neo4j.http.app.Views;
import org.neo4j.http.config.JacksonConfig;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DriverTypeSystemModuleTest {

	private final ObjectMapper objectMapper;

	private final Driver driver;

	DriverTypeSystemModuleTest() {

		driver = mock(Driver.class);
		when(driver.defaultTypeSystem()).thenReturn(InternalTypeSystem.TYPE_SYSTEM);

		var jacksonObjectMapperBuilder = new Jackson2ObjectMapperBuilder();
		new JacksonConfig().objectMapperBuilderCustomizer(driver).customize(jacksonObjectMapperBuilder);

		this.objectMapper = jacksonObjectMapperBuilder.build();
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
			Arguments.of(mockNode(), "{\"s\":\"foo\",\"n\":4711}"),
			Arguments.of(Values.value(LocalDate.of(2022, 10, 24)), "\"2022-10-24\""),
			Arguments.of(Values.value(ZonedDateTime.of(2022, 10, 24, 10, 45, 39, 507000000, ZoneId.of("Europe/Berlin"))), "\"2022-10-24T10:45:39.507+02:00[Europe/Berlin]\""),
			Arguments.of(Values.value(LocalDateTime.of(2022, 10, 24, 10, 45, 39, 507000000)), "\"2022-10-24T10:45:39.507\""),
			Arguments.of(Values.value(LocalTime.of(9, 18, 56, 727000000)), "\"09:18:56.727\""),
			Arguments.of(Values.value(OffsetTime.of(11, 18, 56, 727000000, ZoneOffset.ofHours(2))), "\"11:18:56.727+02:00\""),
			Arguments.of(Values.value(Duration.ofDays(14).plusHours(16).plusMinutes(12)), "\"PT352H12M\""),
			Arguments.of(Values.isoDuration(1, 0, 0, 0), "\"P1M\""),
			Arguments.of(Values.isoDuration(0, 0, 63, 0), "\"PT1M3S\""),
			Arguments.of(Values.point(7203, 2.3, 4.5), "{" +
				"\"type\":\"Point\"," +
				"\"coordinates\":[2.3,4.5]," +
				"\"crs\":{" +
				"\"srid\":7203," +
				"\"name\":\"cartesian\"," +
				"\"type\":\"link\"," +
				"\"properties\":{" +
				"\"href\":\"http://spatialreference.org/ref/sr-org/7203/ogcwkt/\"," +
				"\"type\":\"ogcwkt\"" +
				"}" +
				"}" +
				"}"),
			Arguments.of(Values.point(4326, 56.7, 12.78), "{" +
				"\"type\":\"Point\"," +
				"\"coordinates\":[56.7,12.78]," +
				"\"crs\":{" +
				"\"srid\":4326," +
				"\"name\":\"wgs-84\"," +
				"\"type\":\"link\"," +
				"\"properties\":{" +
				"\"href\":\"http://spatialreference.org/ref/epsg/4326/ogcwkt/\"," +
				"\"type\":\"ogcwkt\"" +
				"}" +
				"}" +
				"}")
		);
	}

	@ParameterizedTest
	@MethodSource
	void shouldSerializeValuesWithDefaultView(Value value, String expected) throws JsonProcessingException {

		var data = Map.of("var", value);
		var json = objectMapper
			.writerWithView(Views.NEO4J_44_DEFAULT.class)
			.writeValueAsString(data);
		assertThat(json).isEqualTo(String.format("{\"var\":%s}", expected.strip()));
	}


}
