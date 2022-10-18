package org.neo4j.http.message;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

final class ParameterConverter {

	private static final Map<String, Function<Object, Object>> CONVERTERS = Map.of(
			"LocalDate", (value -> LocalDate.parse((String) value, DateTimeFormatter.ISO_LOCAL_DATE))
	);

	final static BiFunction<String, Object, Object> CONVERTER = (typeName, value) -> CONVERTERS.getOrDefault(typeName, (o) -> o).apply(value);
}
