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
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.types.InternalTypeSystem;
import org.neo4j.driver.internal.value.NodeValue;
import org.neo4j.driver.types.IsoDuration;
import org.neo4j.driver.types.Node;
import org.neo4j.http.db.AnnotatedQuery.Container;
import org.neo4j.http.db.AnnotatedQuery.ResultFormat;
import org.neo4j.http.app.Views;
import org.neo4j.http.config.JacksonConfig;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResponseRenderingTest {

	private final ObjectMapper objectMapper;

	private final Driver driver;

	ResponseRenderingTest() {

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
			Arguments.of(mockNode(), "{\"$type\":\"Node\",\"_value\":{\"_labels\":[\"L1\",\"L2\",\"$L3\"],\"_props\":{\"s\":\"foo\",\"n\":4711}}}"),
			Arguments.of(Values.value(LocalDateTime.of(2022, 10, 24, 10, 45, 39, 507000000)), "{\"$type\":\"LocalDateTime\",\"_value\":\"2022-10-24T10:45:39.507\"}"),
			Arguments.of(Values.value(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}), "{\"$type\":\"Byte[]\",\"_value\":\"000102030405060708090a0b0c0d0e0f10\"}")
		);
	}

	@Test
	void shouldDeserializeAnnotatedQueries() throws JsonProcessingException {
		var request  = """
			{
			  "statements": [
			    {
			      "statement": "CREATE (bike:Bike {weight: 10}) CREATE (frontWheel:Wheel {spokes: 3}) CREATE (backWheel:Wheel {spokes: 32}) CREATE p1 = (bike)-[:HAS {position: 1}]->(frontWheel) CREATE p2 = (bike)-[:HAS {position: 2} ]->(backWheel) RETURN bike, p1, p2",
			      "parameters": {
			        "nodeId": "The Matrix",
			        "someDate": {"$type": "Date", "_value": "2022-10-21"}
			      },
			      "resultDataContents": ["row", "graph"]
			    }
			  ]
			}
			""";
		var q = objectMapper.readValue(request, Container.class);
		assertThat(q.value()).hasSize(1).first()
			.satisfies(queryWithFormat -> {
				assertThat(queryWithFormat.value().text()).isEqualTo("CREATE (bike:Bike {weight: 10}) CREATE (frontWheel:Wheel {spokes: 3}) CREATE (backWheel:Wheel {spokes: 32}) CREATE p1 = (bike)-[:HAS {position: 1}]->(frontWheel) CREATE p2 = (bike)-[:HAS {position: 2} ]->(backWheel) RETURN bike, p1, p2");
				assertThat(queryWithFormat.value().parameters().get("nodeId")).isEqualTo(Values.value("The Matrix"));
				assertThat(queryWithFormat.value().parameters().get("someDate")).isEqualTo(Values.value(LocalDate.of(2022, 10, 21)));
				assertThat(queryWithFormat.resultDataContents()).containsExactly(ResultFormat.ROW, ResultFormat.GRAPH);
			});
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


	@Nested
	class TemporalAmountAdapterTest {

		private final DefaultResponseModule.TemporalAmountAdapter underTest = new DefaultResponseModule.TemporalAmountAdapter();

		@Test
		public void internallyCreatedTypesShouldBeConvertedCorrect() {

			assertThat(underTest.apply(Values.isoDuration(1, 0, 0, 0).asIsoDuration())).isEqualTo(Period.ofMonths(1));
			assertThat(underTest.apply(Values.isoDuration(1, 1, 0, 0).asIsoDuration())).isEqualTo(Period.ofMonths(1).plusDays(1));
			assertThat(underTest.apply(Values.isoDuration(1, 1, 1, 0).asIsoDuration()))
				.isEqualTo(Values.isoDuration(1, 1, 1, 0).asIsoDuration());
			assertThat(underTest.apply(Values.isoDuration(0, 0, 120, 1).asIsoDuration()))
				.isEqualTo(Duration.ofMinutes(2).plusNanos(1));
		}

		@Test
		public void durationsShouldStayDurations() {

			Duration duration = ChronoUnit.MONTHS.getDuration().multipliedBy(13).plus(ChronoUnit.DAYS.getDuration().multipliedBy(32)).plusHours(25)
				.plusMinutes(120);

			assertThat(underTest.apply(Values.value(duration).asIsoDuration())).isEqualTo(duration);
		}

		@Test
		public void periodsShouldStayPeriods() {

			Period period = Period.between(LocalDate.of(2018, 11, 15), LocalDate.of(2020, 12, 24));

			assertThat(underTest.apply(Values.value(period).asIsoDuration())).isEqualTo(period.normalized());
		}

		@Test // GH-2324
		public void zeroDurationShouldReturnTheIsoDuration() {

			IsoDuration zeroDuration = Values.isoDuration(0, 0, 0, 0).asIsoDuration();
			assertThat(underTest.apply(zeroDuration)).isSameAs(zeroDuration);
		}
	}

}
