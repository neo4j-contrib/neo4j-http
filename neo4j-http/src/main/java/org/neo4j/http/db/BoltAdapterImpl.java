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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.summary.Plan;
import org.neo4j.driver.summary.ResultSummary;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Notes:
 * <ul>
 *     <li>Only credentials supported are {@link String}, mapping to an unencoded password</li>
 * </ul>
 *
 * @author Michael J. Simons
 */
@Service
@Primary
class BoltAdapterImpl extends AbstractNeo4jAdapter {

	private static final Pattern CALL_PATTERN = Pattern.compile("(?i)\\s*CALL\\s*\\{");
	private static final Pattern USING_PERIODIC_PATTERN = Pattern.compile("(?i)\\s*USING\\s+PERIODIC\\s+COMMIT\\s+");

	private final Driver driver;

	BoltAdapterImpl(Driver driver) {
		this.driver = driver;
	}

	@Cacheable("queryTargets")
	@Override
	public Target getQueryTarget(Neo4jPrincipal principal, String query) {

		String theQuery = requireNonNullNonBlank(query);
		var sessionConfig = SessionConfig.builder()
			.withImpersonatedUser(principal.username())
			.withDefaultAccessMode(AccessMode.READ).build();
		try (var session = driver.session(sessionConfig)) {
			var summary = session.run("EXPLAIN " + theQuery).consume();
			return evaluateOperators(getOperators(summary));
		}
	}

	@Cacheable("queryTransactionModes")
	@Override
	public TransactionMode getTransactionMode(Neo4jPrincipal principal, String query) {

		String theQuery = requireNonNullNonBlank(query);
		if (!(CALL_PATTERN.matcher(theQuery).find() || USING_PERIODIC_PATTERN.matcher(theQuery).find())) {
			return TransactionMode.MANAGED;
		}

		var characteristics = QueryCharacteristicsEvaluator.getCharacteristics(query);
		return characteristics.callInTx() || characteristics.periodicCommit() ? TransactionMode.IMPLICIT : TransactionMode.MANAGED;
	}

	private static Set<CypherOperator> getOperators(ResultSummary summary) {

		if (!summary.hasPlan()) {
			return Set.of(CypherOperator.__UNKNOWN__);
		}

		Set<CypherOperator> operators = new HashSet<>();
		traversePlan(summary.database().name(), summary.plan(), operators::add);
		return Set.copyOf(operators);
	}

	private static void traversePlan(String databaseName, Plan plan, Consumer<CypherOperator> operatorSink) {

		var operator = CypherOperator.__UNKNOWN__;
		var operatorType = plan.operatorType().substring(0, plan.operatorType().indexOf(databaseName) - 1);
		try {
			operator = CypherOperator.valueOf(operatorType);
		} catch (IllegalArgumentException e) {
			LOGGER.warning(() -> String.format("An unknown operator was encountered: %s", operatorType));
		}
		operatorSink.accept(operator);
		if (!plan.children().isEmpty()) {
			for (Plan childPlan : plan.children()) {
				traversePlan(databaseName, childPlan, operatorSink);
			}
		}
	}
}
