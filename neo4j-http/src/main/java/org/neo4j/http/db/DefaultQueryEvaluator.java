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
import java.util.logging.Level;

import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.summary.Plan;
import org.neo4j.driver.summary.ResultSummary;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Uses the AST factory basically as a visitor and memorizes if any call {} in transactions of element has been seen.
 *
 * @author Michael J. Simons
 */
@Service
@Profile( "!ssr" )
public class DefaultQueryEvaluator implements QueryEvaluator {

	private final Driver driver;

	private final Mono<Boolean> enterpriseEdition;

	@SuppressWarnings("deprecation")
	public DefaultQueryEvaluator( Driver driver ) {
		this.driver = driver;
		this.enterpriseEdition = QueryEvaluator.isEnterprise( driver );
	}

	@Override
	public Mono<Boolean> isEnterpriseEdition() {
		return enterpriseEdition;
	}

	@Cacheable("executionRequirements")
	@Override
	public Mono<ExecutionRequirements> getExecutionRequirements(Neo4jPrincipal principal, String query) {

		return getQueryTarget(principal, query).zipWith(QueryEvaluator.getTransactionMode(query), ExecutionRequirements::new).cache();
	}

	/**
	 * Computes or retrieves the target against a query should be executed.
	 * <p>
	 * Deprecations are suppressed as we are using the reactivestreams based reactive session.
	 *
	 * @param principal The authenticated principal for whom the query is evaluated
	 * @param query     The string value of a query to be executed, must not be {@literal null} or blank
	 * @return A target for the query
	 * @throws IllegalArgumentException if the query can not be dealt with
	 */
	@SuppressWarnings("deprecation")
	private Mono<Target> getQueryTarget(Neo4jPrincipal principal, String query) {

		var sessionSupplier = isEnterpriseEdition()
			.flatMap(v -> {
				var builder = v ? SessionConfig.builder().withImpersonatedUser(principal.username()) : SessionConfig.builder();
				var sessionConfig = builder
					.withDefaultAccessMode(AccessMode.READ)
					.build();
				return Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig));
			});

		// Invalid queries will end up here for the first time.
		// We don't want to add the additional EXPLAIN to the stack and pointers to the wrong parts don't make much sense
		// In a compressed JSON format either, so we just remove all that stuff with the onErrorMap as last operator
		return Mono.usingWhen(sessionSupplier, session -> Mono.fromDirect(session.run("EXPLAIN " + query)).flatMap(rs -> Mono.fromDirect(rs.consume())), ReactiveSession::close)
			.map(summary -> getOperators(summary).stream().anyMatch(CypherOperator::isUpdating) ? Target.WRITERS : Target.READERS)
			.onErrorMap(DefaultQueryEvaluator::isSyntaxError, e -> new InvalidQueryException(query, (ClientException) e));
	}

	private static boolean isSyntaxError(Throwable e) {
		return e instanceof ClientException ce && "Neo.ClientError.Statement.SyntaxError".equals(ce.code());
	}

	private static Set<CypherOperator> getOperators(ResultSummary summary) {

		if (!summary.hasPlan()) {
			return Set.of(CypherOperator.__UNKNOWN__);
		}

		Set<CypherOperator> operators = new HashSet<>();
		traversePlan(summary.plan(), operators::add);
		return Set.copyOf(operators);
	}

	private static void traversePlan(Plan plan, Consumer<CypherOperator> operatorSink) {

		var operator = CypherOperator.__UNKNOWN__;
		// Can't use the database name here from the summary, as it is broken in the reactive variant, see
		// https://github.com/neo4j/neo4j-java-driver/issues/1320
		var atIndex = plan.operatorType().indexOf('@'); // Aura doesn't have the DB name in the operators… Just because, I guess.
		var operatorType = atIndex < 0 ? plan.operatorType() : plan.operatorType().substring(0, atIndex);
		operatorType = operatorType.replaceAll("\\(\\w+\\)", "");
		try {
			operator = CypherOperator.valueOf(operatorType);
		} catch (IllegalArgumentException e) {
			LOGGER.log(Level.WARNING, "An unknown operator was encountered: {0}", operatorType);
		}
		operatorSink.accept(operator);
		if (!plan.children().isEmpty()) {
			for (Plan childPlan : plan.children()) {
				traversePlan(childPlan, operatorSink);
			}
		}
	}

}
