package org.neo4j.http.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.value.NodeValue;
import org.neo4j.driver.types.Node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DriverTypeSystemModuleTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	DriverTypeSystemModuleTest() {
		this.objectMapper.registerModule(new DriverTypeSystemModule());
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
			Arguments.of(mockNode(), "{\"$type\":\"Node\",\"_value\":{\"_labels\":null,\"_props\":{\"s\":\"foo\",\"n\":4711}}}")
		);
	}

	static NodeValue mockNode() {
		var node = Mockito.mock(Node.class);
		when(node.keys()).thenReturn(List.of("s", "n"));
		when(node.get("s")).thenReturn(Values.value("foo"));
		when(node.get("n")).thenReturn(Values.value(4711));
		return new NodeValue(node);
	}

	@ParameterizedTest
	@MethodSource
	void shouldSerializeValues(Value value, String expected) throws JsonProcessingException {

		var data = Map.of("var", value);
		var json = objectMapper.writeValueAsString(data);
		assertThat(json).isEqualTo(String.format("{\"var\":%s}", expected.strip()));
	}

	@Test
	void f() {
		try (var driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "secret")); var session = driver.session()) {
			var node = session.run("CREATE (n:N {s: 's', n: 1}) RETURN n").single().get(0).asNode();
			node.asMap(Function.identity()).forEach((k, v) -> {
				System.out.println(k + " = " + v.asObject());
			});
		}
	}

	/*
	@JsonTypeInfo(use= JsonTypeInfo.Id.MINIMAL_CLASS, include= JsonTypeInfo.As.WRAPPER_ARRAY) // Include Java class simple-name as JSON property "type"
	@JsonSubTypes({@JsonSubTypes.Type(Car.class), @JsonSubTypes.Type(Aeroplane.class)}) // Required for deserialization only
	public abstract class Vehicle {
	}
	public class Car extends Vehicle {
		public String licensePlate;
	}
	public class Aeroplane extends Vehicle {
		public int wingSpan;
	}

	public class PojoWithTypedObjects {
		public List<Vehicle> items;
	}

	@Test
	void ff() throws JsonProcessingException {
PojoWithTypedObjects f = new PojoWithTypedObjects();
f.items = List.of(new Car(), new Aeroplane());
		System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(f));
	}
*/
}