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

import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Point;

/**
 * "Official" Cypher types.
 *
 * @author Michael J. Simons
 */
enum CypherTypes {

	NULL,

	List,

	Map,

	Boolean,

	Integer,

	Float,

	String,

	ByteArray("Byte[]", CypherTypes::parseByteString, CypherTypes::encode),

	Date(
		v -> LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE),
		v -> DateTimeFormatter.ISO_LOCAL_DATE.format(v.asLocalDate())
	),

	Time(
		v -> OffsetTime.parse(v, DateTimeFormatter.ISO_OFFSET_TIME),
		v -> DateTimeFormatter.ISO_OFFSET_TIME.format(v.asOffsetTime())
	),

	LocalTime(
		v -> java.time.LocalTime.parse(v, DateTimeFormatter.ISO_LOCAL_TIME),
		v -> DateTimeFormatter.ISO_LOCAL_TIME.format(v.asLocalTime())
	),

	DateTime(
		v -> ZonedDateTime.parse(v, DateTimeFormatter.ISO_ZONED_DATE_TIME),
		v -> DateTimeFormatter.ISO_ZONED_DATE_TIME.format(v.asZonedDateTime())
	),

	LocalDateTime(
		v -> java.time.LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
		v -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(v.asLocalDateTime())
	),

	Duration(java.time.Duration::parse, v -> v.asIsoDuration().toString()),

	Period(java.time.Period::parse, v -> v.asIsoDuration().toString()),

	Point(CypherTypes::parsePoint, null),

	Node,

	Relationship,

	Path;

	private final String value;

	private final Function<java.lang.String, Value> reader;
	private final Function<Value, java.lang.String> writer;

	CypherTypes() {
		this(null, null, null);
	}

	CypherTypes(Function<String, Object> reader, Function<Value, String> writer) {
		this(null, reader, writer);
	}

	CypherTypes(String value, Function<String, Object> reader, Function<Value, String> writer) {

		this.value = value == null ? this.name() : value;
		this.reader = reader == null ? null : reader.andThen(Values::value);
		this.writer = writer;
	}

	static CypherTypes byNameOrValue(String nameOrValue) {
		return Arrays.stream(CypherTypes.values()).filter(ct -> ct.value.equals(nameOrValue))
			.findFirst().orElseGet(() -> CypherTypes.valueOf(nameOrValue));
	}

	public String getValue() {
		return value;
	}

	/**
	 * {@return optional reader if this type can be read directly}
	 */
	public Function<java.lang.String, Value> getReader() {
		return reader;
	}

	/**
	 * {@return optional writer if this type can be written directly}
	 */
	public Function<Value, java.lang.String> getWriter() {
		return writer;
	}

	private static byte[] parseByteString(String rawInput) {
		var input = rawInput.replaceAll("\s*", "");
		int inputLength = input.length();
		var result = new byte[inputLength / 2];

		for (int i = 0; i < inputLength; i += 2) {
			result[i / 2] = (byte) ((Character.digit(input.charAt(i), 16) << 4)
				+ Character.digit(input.charAt(i + 1), 16));
		}

		return result;
	}

	private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

	private static String encode(Value value) {
		byte[] bytes = value.asByteArray();
		final StringBuilder sb = new StringBuilder(2 * bytes.length);
		for (byte b : bytes) {
			sb.append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
		}
		return sb.toString();
	};

	private static final Pattern WKT_PATTERN = Pattern.compile("SRID=(\\d+);\\s*POINT\\(\\s*(\\S+)\\s+(\\S+)\\s*(\\S?)\\)");

	/**
	 * Pragmatic parsing of the Neo4j Java Driver's {@link org.neo4j.driver.types.Point} class
	 * This method does not check if the parameters align with the given coordinate system or if the coordinate system code is valid.
	 *
	 * @param input WKT representation of a point
	 */
	private static Point parsePoint(String input) {
		var matcher = WKT_PATTERN.matcher(input);

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Illegal pattern"); //todo add right pattern syntax in exception message
		}

		var srid = java.lang.Integer.parseInt(matcher.group(1));
		var x = Double.parseDouble(matcher.group(2));
		var y = Double.parseDouble(matcher.group(3));
		var z = matcher.group(4);
		if (z != null && !z.isBlank()) {
			return Values.point(srid, x, y, Double.parseDouble(z)).asPoint();
		} else {
			return Values.point(srid, x, y).asPoint();
		}
	}
}
