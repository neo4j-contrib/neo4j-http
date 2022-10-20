package org.neo4j.http.message;

import org.neo4j.driver.types.Point;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public record PointParameter(int srid, double x, double y, double z) implements Point {

	private final static Pattern WKT_PATTERN = Pattern.compile("SRID=(\\d+);\\s*POINT\\(\\s*(\\S+)\\s+(\\S+)\\s*(\\S?)\\)");

	public static PointParameter of(String input) {
		var matcher = WKT_PATTERN.matcher(input);

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Illegal patter"); //todo add right pattern syntax in exception message
		}

		return new PointParameter(
				Integer.parseInt(matcher.group(1)),
				Double.parseDouble(matcher.group(2)),
				Double.parseDouble(matcher.group(3)),
				StringUtils.hasText(matcher.group(4)) ? Double.parseDouble(matcher.group(4)) : 0
		);
	}
}