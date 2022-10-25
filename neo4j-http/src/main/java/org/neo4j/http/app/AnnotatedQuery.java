package org.neo4j.http.app;

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
		/** Standard since Neo4j 3.5, will always be included. */
		ROW,
		/** A somewhat graph-like format, needs to be explicitly requested. */
		GRAPH
	}

	public AnnotatedQuery {
		resultDataContents = resultDataContents == null || resultDataContents.length == 0 ? new ResultFormat[] {ResultFormat.ROW} : resultDataContents;
	}

	/**
	 * This is a container for {@link AnnotatedQuery annotated queries}
	 * @param value
	 */
	public record Container(List<AnnotatedQuery> value) {

		public Container {
			value = List.copyOf(value);
		}
	}
}
