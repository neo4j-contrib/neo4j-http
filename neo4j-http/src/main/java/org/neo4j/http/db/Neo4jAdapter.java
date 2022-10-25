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

import org.neo4j.driver.Record;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Access to Neo4j via an adapter. In most Spring applications I'm a fan of _not_ using service interfaces but for this
 * project this adapter is meaningful: Its primary implementation will be based on the Driver and thus the Bolt protocol.
 * In a future step however we are planing to deploy the http artifact in such a way that it can access the embedded API
 * directly.
 *
 * @author Michael J. Simons
 */
public interface Neo4jAdapter {

	/**
	 * Streams the records of the given query
	 * @param principal The authenticated principal
	 * @param query The query to execute
	 * @return A stream of records
	 */
	Flux<Record> stream(Neo4jPrincipal principal, String query);

	/**
	 * Executes one or more queries and eagerly collects toe results into a {@link ResultContainer}.
	 * @param principal The authenticated principal
	 * @param query The query to execute
	 * @param additionalQueries Additional queries to execute
	 * @return An eagerly populated result container
	 */
	Mono<ResultContainer> run(Neo4jPrincipal principal, AnnotatedQuery query, AnnotatedQuery... additionalQueries);
}
