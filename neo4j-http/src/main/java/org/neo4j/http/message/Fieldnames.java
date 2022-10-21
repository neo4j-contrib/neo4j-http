package org.neo4j.http.message;

/**
 * Some API specific field names. {@literal $type} seems to be widely used as an actual type denominator for non-standard
 * JSON types.
 * <p>
 * It will clash with the term "type" for relationship types. But in both cases the general term is the best. So it makes sense
 * to maybe separate everything that is "neo4j" specific (here, labels and types for nodes and relationships) as well as the
 * value of a non-standard json type with an underscore ({@literal _}).
 */
final class Fieldnames {

	static final String CYPHER_TYPE = "$type";
	static final String CYPHER_VALUE = "_value";
	static final String LABELS = "_labels";
	static final String RELATIONSHIP_TYPE = "_type";
	static final String PROPERTIES = "_props";


	private Fieldnames() {
	}
}
