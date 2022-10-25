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
package org.neo4j.http.db;

import java.util.List;

import org.neo4j.driver.Query;

/**
 * An annotated query that optionally contains a flag whether to include stats or not and one or more requested result contents.
 * <p>
 * Non-standard JSON types can be expressed by simple wrapper objects in the following shape
 * <pre>
 *   {"$type": "LocalDate", "_value":"2022-10-20""}
 * </pre>
 * All Cypher types except {@literal Node}, {@literal Relationship} and {@literal Path} as listed in
 * <a href="https://neo4j.com/docs/java-manual/current/cypher-workflow/#java-driver-type-mapping">Java-Driver-Type-Mapping</a>
 * are supported (Look for the "Cypher Type" column) are supported as incoming parameters. The following will require their
 * type to explicitly listed as above:
 * <ul>
 *     <li>{@literal Date}</li>
 *     <li>{@literal Time}</li>
 *     <li>{@literal LocalTime}</li>
 *     <li>{@literal DateTime}</li>
 *     <li>{@literal LocalDateTime}</li>
 *     <li>{@literal Duration}</li>
 *     <li>{@literal Period}</li>
 *     <li>{@literal Point}</li>
 *     <li>{@literal ByteArray}</li>
 * </ul>
 *
 * <p>
 * By wrapping one or more {@link AnnotatedQuery annoted queries} into a {@link  Container container}, a format similar to
 * <a href="https://neo4j.com/docs/http-api/current/actions/query-format/">Neo4j's HTTP query format</a> can be expressed.
 *
 * @author Gerrit Meier
 * @author Michael J. Simons
 * @param value              The actual query
 * @param includeStats       flag to include stats or not
 * @param resultDataContents One or more formats, not applicable to the streaming API
 */
public record AnnotatedQuery(Query value, boolean includeStats, ResultFormat... resultDataContents) {

	/**
	 * Possible result formats
	 */
	public enum ResultFormat {
		/**
		 * Standard since Neo4j 3.5, will always be included.
		 */
		ROW,
		/**
		 * A somewhat graph-like format, needs to be explicitly requested.
		 */
		GRAPH;
	}

	/**
	 * This is a container for {@link AnnotatedQuery annotated queries}
	 *
	 * @param value the content of this container
	 */
	public record Container(List<AnnotatedQuery> value) {
	}

	/**
	 * Makes sure the {@link #resultDataContents} are never empty
	 * @param value              The actual query
	 * @param includeStats       flag to include stats or not
	 * @param resultDataContents One or more formats, not applicable to the streaming API
	 */
	public AnnotatedQuery {
		resultDataContents = resultDataContents == null || resultDataContents.length == 0 ? new ResultFormat[] {ResultFormat.ROW} : resultDataContents;
	}

	/**
	 * {@return the text of the annotated query}
	 */
	public String text() {
		return value.text();
	}
}
