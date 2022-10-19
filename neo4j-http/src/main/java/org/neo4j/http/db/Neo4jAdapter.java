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

import reactor.core.publisher.Flux;

/**
 * Access to Neo4j via an adapter. In most Spring applications I'm a fan of _not_ using service interfaces but for this
 * project this adapter is meaningful: Its primary implementation will be based on the Driver and thus the Bolt protocol.
 * In a future step however we are planing to deploy the http artifact in such a way that it can access the embedded API
 * directly.
 *
 * @author Michael J. Simons
 */
public sealed interface Neo4jAdapter permits AbstractNeo4jAdapter {

	// TODO very much WIP just to get a feels, needs multiple queries and ofc parameters
	Flux<Wip> stream(Neo4jPrincipal principal, String query);
}
