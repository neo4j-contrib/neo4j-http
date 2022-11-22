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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Greg Woods
 */
class QueryEvaluatorTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"""
					UNWIND [0, 1, 2] AS x
					CALL {
					  WITH x
					  RETURN x * 10 AS y
					}
					RETURN x, y
					""",
			"CREATE (a:`USING PERIODIC COMMIT `) RETURN a",
			"CREATE (a:`CALL {WITH WHATEVER} IN TRANSACTIONS`) RETURN a"
	})
	void shouldDetectManagedTransactions(String query) {
		QueryEvaluator.getTransactionMode(query)
				.as(StepVerifier::create)
				.expectNext(QueryEvaluator.TransactionMode.MANAGED)
				.verifyComplete();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"""
					USING PERIODIC COMMIT 500 LOAD CSV FROM 'file:///artists.csv' AS line
					CREATE (:Artist {name: line[1], year: toInteger(line[2])})
					""",
			"   USING PERIODIC COMMIT 500 LOAD CSV FROM 'file:///artists.csv' AS line CREATE (:Artist {name: line[1], year: toInteger(line[2])})",
			"""
					LOAD CSV FROM 'file:///friends.csv' AS line
					CALL {
					  WITH line
					  CREATE (:PERSON {name: line[1], age: toInteger(line[2])})
					} IN TRANSACTIONS
					"""
	})
	void shouldDetectImplicitTransactions(String query) {
		QueryEvaluator.getTransactionMode(query)
				.as(StepVerifier::create)
				.expectNext(QueryEvaluator.TransactionMode.IMPLICIT)
				.verifyComplete();
	}


	@ParameterizedTest
	@ValueSource(strings = {
			"""
					LOAD CSV FROM 'file:///friends.csv' AS line
					CALL {
					  WITH line
					  CREATE (:PERSON {name: line[1], age: toInteger(line[2])})
					} IN TRANSACTIONS
					"""
	})
	void shouldRecogniseCallInTx(String query) {
		var characteristics = QueryEvaluator.getCharacteristics(query);
		assertThat(characteristics.callInTx()).isTrue();
		assertThat(characteristics.periodicCommit()).isFalse();

	}

	@ParameterizedTest
	@ValueSource(
			strings = {
					"""
							USING PERIODIC COMMIT 500 LOAD CSV FROM 'file:///artists.csv' AS line
							CREATE (:Artist {name: line[1], year: toInteger(line[2])})
							""",
					"   USING PERIODIC COMMIT 500 LOAD CSV FROM 'file:///artists.csv' AS line CREATE (:Artist {name: line[1], year: toInteger(line[2])})"
			})
	void shouldRecognisePeriodicCommit(String query) {
		var characteristics = QueryEvaluator.getCharacteristics(query);
		assertThat(characteristics.periodicCommit()).isTrue();
		assertThat(characteristics.callInTx()).isFalse();
	}
}
