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

import java.util.Set;

/**
 * The default adapter uses Springs declarative caching mechanism and the non-sealed base class allows actual implementations
 * to break the seal without allowing arbitrary other implementations into the hierachy.
 *
 * @author Michael J. Simons
 */
non-sealed abstract class AbstractNeo4jAdapter implements Neo4jAdapter {

	/**
	 * Evaluates the list of given {@link CypherOperator cypher operators} for operators we know as updating operators.
	 * In case any updating operator is found, we will delegate
	 *
	 * @param cypherOperators A set of cypher operators
	 * @return The target to run a given query against
	 */
	final Target evaluateOperators(Set<CypherOperator> cypherOperators) {

		return cypherOperators.stream().anyMatch(CypherOperator::isUpdating) ? Target.WRITERS : Target.READERS;
	}
}
