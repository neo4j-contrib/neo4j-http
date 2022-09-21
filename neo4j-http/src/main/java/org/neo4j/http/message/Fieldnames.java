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
	static final String ID = "_id";
	static final String ELEMENT_ID = "_element_id";
	static final String RELATIONSHIP_TYPE = "_type";
	static final String PROPERTIES = "_props";

	private Fieldnames() {
	}
}
