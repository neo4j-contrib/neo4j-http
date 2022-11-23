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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.neo4j.driver.Driver;

import reactor.core.publisher.Mono;

/**
 * A strategy for determining the execution requirements of a query.
 *
 * @author Michael J. Simons
 */
public interface QueryEvaluator {

	/**
	 * Shared logger for all instances / variants
	 */
	Logger LOGGER = Logger.getLogger(QueryEvaluator.class.getName());

	/**
	 * Creates a new {@link QueryEvaluator} using the capabilities of the given connection.
	 * @param driver connected to an instance that has a given set of {@link Capabilities}.
	 * @param capabilities capabilities of the instance against the driver is connected to
	 * @return a {@link QueryEvaluator}
	 */
	static QueryEvaluator create(Driver driver, Capabilities capabilities) {

		if (capabilities.ssrAvailable()) {
			LOGGER.log(Level.INFO, "Using SSR");
			return new DefaultQueryEvaluator(driver);
		}
		LOGGER.log(Level.WARNING, "Using client side query evaluation, some queries might get routed wrong");
		return new DefaultQueryEvaluator(driver);
	}

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
		WRITERS
	}

	/**
	 * Indicator of the transaction mode to use
	 */
	enum TransactionMode {
		/**
		 * Use managed transactions (aka transaction functions)
		 */
		MANAGED,
		/**
		 * Use implicit transactions (aka auto commit)
		 */
		IMPLICIT
	}

	/**
	 * The execution requirements of a query
	 *
	 * @param target          The target (readers or writers)
	 * @param transactionMode (possible tx modes)
	 */
	record ExecutionRequirements(Target target, TransactionMode transactionMode) {
	}

	Mono<Boolean> isEnterpriseEdition();

	/**
	 * Retrieves the execution requirements of a query. Most implementations will actually cache it.
	 *
	 * @param principal The authenticated principal for whom the query is evaluated
	 * @param query     The string value of a query to be executed, must not be {@literal null} or blank
	 * @return The characteristics of the query
	 */
	Mono<ExecutionRequirements> getExecutionRequirements(Neo4jPrincipal principal, String query);
}
