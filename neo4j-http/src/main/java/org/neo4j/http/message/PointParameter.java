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

import org.neo4j.driver.types.Point;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Implementation of the Neo4j Java Driver's {@link Point} class with pragmatic parsing of {@link String}.
 * This class does not check if the parameters align with the given coordinate system or if the coordinate system code is valid.
 *
 * @author Gerrit Meier
 *
 * @param srid The numeric code representation of the coordinate system:
 *             - WGS84: 		4326
 *             - WGS84 3D: 		4979
 *             - Cartesian: 	7203
 *             - Cartesion 3D: 	9157
 *
 * @param x x-Coordinate
 * @param y y-Coordinate
 * @param z z-Coordinate (optional)
 */
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
