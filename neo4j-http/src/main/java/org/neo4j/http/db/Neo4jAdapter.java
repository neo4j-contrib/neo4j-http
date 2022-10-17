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

import java.util.logging.Logger;

import org.springframework.security.core.Authentication;

/**
 * Access to Neo4j via an adapter. In most Spring applications I'm a fan of _not_ using service interfaces but for this
 * project this adapter is meaningful: Its primary implementation will be based on the Driver and thus the Bolt protocol.
 * In a future step however we are planing to deploy the http artifact in such a way that it can access the embedded API
 * directly.
 *
 * @author Michael J. Simons
 */
public sealed interface Neo4jAdapter permits AbstractNeo4jAdapter {

	Logger LOGGER = Logger.getLogger(Neo4jAdapter.class.getName());

	/**
	 * Basically a copy of the drivers access mode. As there is ongoing debate to rename this we stay independent.
	 * This access mode is not about write protection or similar, but only
	 */
	enum Target {
		/**
		 * Can safely be routed to readers.
		 */
		READERS,
		/**
		 * Requires to be routed to writers.
		 */
		WRITERS,
		/**
		 * Can't be decided from the implementing adapter, should be routed to writers if possible.
		 */
		UNDECIDED
	}

	/**
	 * The target against a query should be executed.
	 *
	 * @param principal The authenticated principal for whom the query is evaluated
	 * @param query     The string value of a query to be executed
	 * @return A target for the query
	 * @throws IllegalArgumentException if the query can not be dealt with
	 */
	Target getQueryTarget(Neo4jPrincipal principal, String query);

	/**
	 * A helper method for drivers that don't allow to use any authentication for impersonation
	 *
	 * @param principal   The principal to be authenticated
	 * @param credentials The password to try to authenticate with
	 * @return {@literal true} if the given {@link Authentication} can be safely used as impersonated user
	 */
	boolean canImpersonate(Neo4jPrincipal principal, Object credentials);
}
