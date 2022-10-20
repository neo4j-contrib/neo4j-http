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

import java.util.List;
import java.util.Map;

/**
 * This represents the incoming JSON query request.
 * It is inspired by <a href="https://neo4j.com/docs/http-api/current/actions/query-format/">Neo4j's HTTP query format</a>.
 *
 * Offers support for complex types like dates and points by defining
 * {@code {"type": "LocalDate", "value":"2022-10-20""}}
 *
 * @author Gerrit Meier
 *
 * @param statements collection of statements with their parameters
 */
public record CypherRequest(List<StatementAndParameter> statements) {

	/**
	 * Representation of a single Cypher statement with its parameters
	 * @param statement the Cypher statement
	 * @param parameters the map of parameters to be used with the statement
	 */
	public record StatementAndParameter(String statement, Map<String, Object> parameters) {

	}

}
