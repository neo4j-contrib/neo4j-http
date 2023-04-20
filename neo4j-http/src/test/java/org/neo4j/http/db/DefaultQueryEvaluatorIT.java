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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.http.db.QueryEvaluator.Target;
import org.neo4j.http.db.QueryEvaluator.TransactionMode;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.Neo4jLabsPlugin;

import reactor.test.StepVerifier;

/**
 * @author Michael J. Simons
 */
class DefaultQueryEvaluatorIT {

	static final String DEFAULT_NEO4J_IMAGE = System.getProperty("neo4j-http.default-neo4j-image");

	@SuppressWarnings("resource")
	private static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(DEFAULT_NEO4J_IMAGE)
		.withLabsPlugins(Neo4jLabsPlugin.APOC_CORE)
		.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
		.withReuse(true);

	private static Driver driver;

	@BeforeAll
	static void prepareNeo4j() {
		neo4j.start();
		driver = GraphDatabase.driver(neo4j.getBoltUrl(), AuthTokens.basic("neo4j", neo4j.getAdminPassword()), Config.builder().withLogging(Logging.none()).build());
	}

	@AfterAll
	static void closeDriver() {

		driver.closeAsync();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"CREATE (n:Foo) RETURN n",
		"""
		CALL apoc.case([
			false, 'RETURN "firstFalse" as b',
			false, 'RETURN "secondFalse" as b',
			true, 'RETURN "firstTrue" as b'
		])
		"""
	})
	void shouldDetectUpdatingOperators(String query) {

		var evaluator = new DefaultQueryEvaluator(driver);
		evaluator.getExecutionRequirements(new Neo4jPrincipal("neo4j", "neo4j"), query)
			.map(QueryEvaluator.ExecutionRequirements::target)
			.as(StepVerifier::create)
			.expectNext(Target.WRITERS)
			.verifyComplete();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"MATCH (n) RETURN n",
		"MATCH (n:Foo) WHERE n.id = apoc.create.uuid() RETURN n",
	})
	void shouldDetectNonUpdatingOperators(String query) {

		var evaluator = new DefaultQueryEvaluator(driver);
		evaluator.getExecutionRequirements(new Neo4jPrincipal("neo4j","neo4j"), query)
			.map(QueryEvaluator.ExecutionRequirements::target)
			.as(StepVerifier::create)
			.expectNext(Target.READERS)
			.verifyComplete();
	}

	@Test
	void shouldApplyPrincipal() {

		var evaluator = new DefaultQueryEvaluator(driver);
		var principal = new Neo4jPrincipal("foo","neo4j");
		evaluator.getExecutionRequirements(principal, "MATCH (n) RETURN n")
				.as(StepVerifier::create)
					.expectErrorMessage("Cannot impersonate user 'foo'.")
						.verify();
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
	void shouldDetectImplicitTransactionNeeds(String query) {

		var evaluator = new DefaultQueryEvaluator(driver);
		evaluator.getExecutionRequirements(new Neo4jPrincipal("neo4j","neo4j"), query)
			.map(QueryEvaluator.ExecutionRequirements::transactionMode)
			.as(StepVerifier::create)
			.expectNext(TransactionMode.IMPLICIT)
			.verifyComplete();
	}

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
	void shouldDetectManagedTransactionNeeds(String query) {

		var evaluator = new DefaultQueryEvaluator(driver);
		evaluator.getExecutionRequirements(new Neo4jPrincipal("neo4j","neo4j"), query)
			.map(QueryEvaluator.ExecutionRequirements::transactionMode)
			.as(StepVerifier::create)
			.expectNext(TransactionMode.MANAGED)
			.verifyComplete();
	}
}
