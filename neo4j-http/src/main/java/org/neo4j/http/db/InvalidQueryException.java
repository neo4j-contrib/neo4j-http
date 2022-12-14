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

import java.io.Serial;

import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.Neo4jException;

/**
 * Thrown by the {@link QueryEvaluator} in case of a syntax error in a Cypher statement.
 *
 * @author Michael J. Simons
 */
public final class InvalidQueryException extends Neo4jException {

	@Serial
	private static final long serialVersionUID = -2496202925680143919L;

	/**
	 * The invalid query, supposed to be the normalized version, without any additional {@literal EXPLAIN}.
	 */
	private final String query;

	InvalidQueryException(String query, ClientException cause) {
		super(cause.code(), "Invalid query:" + query, cause);
		this.query = query;
	}

	/**
	 * {@return the query that caused this exception}
	 */
	public String getQuery() {
		return query;
	}
}
