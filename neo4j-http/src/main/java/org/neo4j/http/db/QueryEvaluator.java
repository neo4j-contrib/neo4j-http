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
import java.util.regex.Pattern;

import reactor.core.publisher.Mono;

import org.neo4j.cypher.internal.parser.javacc.Cypher;
import org.neo4j.cypher.internal.parser.javacc.CypherCharStream;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactivestreams.ReactiveSession;

/**
 * A strategy for determining the execution requirements of a query.
 *
 * @author Michael J. Simons
 */
public interface QueryEvaluator {

	Pattern CALL_PATTERN = Pattern.compile( "(?i)\\s*CALL\\s*\\{");
	Pattern USING_PERIODIC_PATTERN = Pattern.compile("(?i)\\s*USING\\s+PERIODIC\\s+COMMIT\\s+");

	/**
	 * Shared logger for all instances / variants
	 */
	Logger LOGGER = Logger.getLogger(QueryEvaluator.class.getName());

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

	/**
	 * Retrieves easy to determine query details such as call in tx / periodic commit.
	 *
	 * @param query The query to evaluate
	 * @return The details of the query
	 */
	static QueryCharacteristics getCharacteristics(String query) {
		ASTFactoryImpl astFactory = new ASTFactoryImpl();
		try {
			// We are using the side effects of the factory
			@SuppressWarnings("unused")
			var statement = new Cypher<>(astFactory,
					ASTExceptionFactoryImpl.INSTANCE,
					new CypherCharStream(query)).Statement();
			return new QueryCharacteristics(astFactory.getHasSeenCallInTx(), astFactory.getHasSeenPeriodicCommit());
		} catch (Exception e) {
			return new QueryCharacteristics(false, false);
		}
	}

	/**
	 * Computes or retrieves the transaction mode required by the query.
	 *
	 * @param query The string value of a query to be executed, must not be {@literal null} or blank
	 * @return The transaction mode required by the query
	 */
	static Mono<TransactionMode> getTransactionMode(String query) {

		var result = TransactionMode.MANAGED;
		if (CALL_PATTERN.matcher(query).find() || USING_PERIODIC_PATTERN.matcher(query).find()) {
			var characteristics = QueryEvaluator.getCharacteristics(query);
			result = characteristics.callInTx() || characteristics.periodicCommit() ? TransactionMode.IMPLICIT : TransactionMode.MANAGED;
		}

		return Mono.just(result);
	}

	static Mono<Boolean> isEnterprise(Driver driver) {
		return Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class)),
				rxSession -> Mono.fromDirect(rxSession.run("CALL dbms.components() YIELD edition RETURN toLower(edition) = 'enterprise'"))
						.flatMap(rs -> Mono.fromDirect(rs.records())).map(record -> record.get(0).asBoolean()),
				ReactiveSession::close
		).cache();
	}

}
