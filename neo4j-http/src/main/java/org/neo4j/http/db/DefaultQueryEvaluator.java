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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.neo4j.cypher.internal.ast.factory.ASTExceptionFactory;
import org.neo4j.cypher.internal.ast.factory.ASTFactory;
import org.neo4j.cypher.internal.ast.factory.ASTFactory.NULL;
import org.neo4j.cypher.internal.ast.factory.AccessType;
import org.neo4j.cypher.internal.ast.factory.ActionType;
import org.neo4j.cypher.internal.ast.factory.ConstraintType;
import org.neo4j.cypher.internal.ast.factory.ConstraintVersion;
import org.neo4j.cypher.internal.ast.factory.CreateIndexTypes;
import org.neo4j.cypher.internal.ast.factory.HintIndexType;
import org.neo4j.cypher.internal.ast.factory.ParameterType;
import org.neo4j.cypher.internal.ast.factory.ScopeType;
import org.neo4j.cypher.internal.ast.factory.ShowCommandFilterTypes;
import org.neo4j.cypher.internal.ast.factory.SimpleEither;
import org.neo4j.cypher.internal.parser.javacc.Cypher;
import org.neo4j.cypher.internal.parser.javacc.CypherCharStream;
import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.summary.Plan;
import org.neo4j.driver.summary.ResultSummary;
import org.springframework.cache.annotation.Cacheable;

import reactor.core.publisher.Mono;

/**
 * Uses the AST factory basically as a visitor and memorizes if any call {} in transactions of element has been seen.
 *
 * @author Michael J. Simons
 */
class DefaultQueryEvaluator implements QueryEvaluator {

	private static final Pattern CALL_PATTERN = Pattern.compile("(?ims)(?<!`)([^`\\s*]\\s*+CALL\\s*\\{.*}\\s*IN\\s+TRANSACTIONS)(?!`)");
	private static final Pattern USING_PERIODIC_PATTERN = Pattern.compile("(?ims)(?<!`)(([^`\\s*]|^)\\s*+USING\\s+PERIODIC\\s+COMMIT\\s+)(?!`)");

	private final Driver driver;

	private final Mono<Boolean> enterpriseEdition;

	DefaultQueryEvaluator(Driver driver) {
		this.driver = driver;
		this.enterpriseEdition = Mono.usingWhen(
			Mono.fromCallable(() -> driver.session(ReactiveSession.class)),
			rxSession -> Mono.fromDirect(rxSession.run("CALL dbms.components() YIELD edition RETURN toLower(edition) = 'enterprise'")).flatMap(rs -> Mono.fromDirect(rs.records())).map(record -> record.get(0).asBoolean()),
			ReactiveSession::close
		).cache();
	}

	@Override
	public Mono<Boolean> isEnterpriseEdition() {
		return enterpriseEdition;
	}

	@Cacheable("executionRequirements")
	@Override
	public Mono<ExecutionRequirements> getExecutionRequirements(Neo4jPrincipal principal, String query) {

		return getQueryTarget(principal, query).zipWith(getTransactionMode(query), ExecutionRequirements::new).cache();
	}

	/**
	 * Computes or retrieves the target against a query should be executed.
	 * <p>
	 *
	 * @param principal The authenticated principal for whom the query is evaluated
	 * @param query     The string value of a query to be executed, must not be {@literal null} or blank
	 * @return A target for the query
	 * @throws IllegalArgumentException if the query can not be dealt with
	 */
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

	/**
	 * Computes or retrieves the transaction mode required by the query.
	 *
	 * @param query The string value of a query to be executed, must not be {@literal null} or blank
	 * @return The transaction mode required by the query
	 */
	private Mono<TransactionMode> getTransactionMode(String query) {

		var result = TransactionMode.MANAGED;
		if (CALL_PATTERN.matcher(query).find() || USING_PERIODIC_PATTERN.matcher(query).find()) {
			var characteristics = DefaultQueryEvaluator.getCharacteristics(query);
			result = characteristics.callInTx() || characteristics.periodicCommit() ? TransactionMode.IMPLICIT : TransactionMode.MANAGED;
		}

		return Mono.just(result);
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

	private record QueryCharacteristics(boolean callInTx, boolean periodicCommit) {
	}

	/**
	 * Retrieves easy to determine query details such as call in tx / periodic commit.
	 *
	 * @param query The query to evaluate
	 * @return The details of the query
	 */
	private static QueryCharacteristics getCharacteristics(String query) {
		ASTFactoryImpl astFactory = new ASTFactoryImpl();
		try {
			// We are using the side effects of the factory
			@SuppressWarnings("unused")
			var statement = new Cypher<>(astFactory,
				ASTExceptionFactoryImpl.INSTANCE,
				new CypherCharStream(query)).Statement();
			return new QueryCharacteristics(astFactory.hasSeenCallInTx.get(), astFactory.hasSeenPeriodicCommit.get());
		} catch (Exception e) {
			return new QueryCharacteristics(false, false);
		}
	}

	private enum ASTExceptionFactoryImpl implements ASTExceptionFactory {
		INSTANCE;

		@Override
		public Exception syntaxException(String got, List<String> expected, Exception source, int offset, int line, int column) {
			return new RuntimeException(source.getMessage());
		}

		@Override
		public Exception syntaxException(Exception source, int offset, int line, int column) {
			return new RuntimeException(source.getMessage());
		}
	}

	private static class ASTFactoryImpl implements ASTFactory<NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL,
		NULL> {

		private final AtomicBoolean hasSeenCallInTx = new AtomicBoolean(false);

		private final AtomicBoolean hasSeenPeriodicCommit = new AtomicBoolean(false);


		@Override
		public NULL newSingleQuery(NULL aNull, List<NULL> list) {
			return null;
		}

		@Override
		public NULL newSingleQuery(List<NULL> list) {
			return null;
		}

		@Override
		public NULL newUnion(NULL aNull, NULL aNull2, NULL query1, boolean b) {
			return null;
		}

		@Override
		public NULL periodicCommitQuery(NULL aNull, NULL pos1, String s, NULL aNull2, List<NULL> list) {
			this.hasSeenPeriodicCommit.compareAndSet(false, true);
			return null;
		}

		@Override
		public NULL useClause(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL newReturnClause(NULL aNull, boolean b, NULL aNull2, List<NULL> list, NULL pos1, NULL aNull3, NULL pos2, NULL expression1, NULL pos3) {
			return null;
		}

		@Override
		public NULL newReturnItems(NULL aNull, boolean b, List<NULL> list) {
			return null;
		}

		@Override
		public NULL newReturnItem(NULL aNull, NULL aNull2, NULL aNull3) {
			return null;
		}

		@Override
		public NULL newReturnItem(NULL aNull, NULL aNull2, int i, int i1) {
			return null;
		}

		@Override
		public NULL orderDesc(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL orderAsc(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL whereClause(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL withClause(NULL aNull, NULL aNull2, NULL aNull3) {
			return null;
		}

		@Override
		public NULL matchClause(NULL aNull, boolean b, List<NULL> list, NULL pos1, List<NULL> list1, NULL aNull2) {
			return null;
		}

		@Override
		public NULL usingIndexHint(NULL aNull, NULL aNull2, String s, List<String> list, boolean b, HintIndexType hintIndexType) {
			return null;
		}

		@Override
		public NULL usingJoin(NULL aNull, List<NULL> list) {
			return null;
		}

		@Override
		public NULL usingScan(NULL aNull, NULL aNull2, String s) {
			return null;
		}

		@Override
		public NULL createClause(NULL aNull, List<NULL> list) {
			return null;
		}

		@Override
		public NULL setClause(NULL aNull, List<NULL> list) {
			return null;
		}

		@Override
		public NULL setProperty(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL setVariable(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL addAndSetVariable(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL setLabels(NULL aNull, List<StringPos<NULL>> list) {
			return null;
		}

		@Override
		public NULL removeClause(NULL aNull, List<NULL> list) {
			return null;
		}

		@Override
		public NULL removeProperty(NULL aNull) {
			return null;
		}

		@Override
		public NULL removeLabels(NULL aNull, List<StringPos<NULL>> list) {
			return null;
		}

		@Override
		public NULL deleteClause(NULL aNull, boolean b, List<NULL> list) {
			return null;
		}

		@Override
		public NULL unwindClause(NULL aNull, NULL aNull2, NULL aNull3) {
			return null;
		}

		@Override
		public NULL mergeClause(NULL aNull, NULL aNull2, List<NULL> list, List<MergeActionType> list1, List<NULL> list2) {
			return null;
		}

		@Override
		public NULL callClause(NULL aNull, NULL pos1, NULL pos2, NULL pos3, List<String> list, String s, List<NULL> list1, boolean b, List<NULL> list2, NULL aNull2) {
			return null;
		}

		@Override
		public NULL callResultItem(NULL aNull, String s, NULL aNull2) {
			return null;
		}

		@Override
		public NULL namedPattern(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL shortestPathPattern(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL allShortestPathsPattern(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL everyPathPattern(List<NULL> list, List<NULL> list1) {
			return null;
		}

		@Override
		public NULL nodePattern(NULL aNull, NULL aNull2, List<StringPos<NULL>> list, NULL aNull3, NULL expression1) {
			return null;
		}

		@Override
		public NULL relationshipPattern(NULL aNull, boolean b, boolean b1, NULL aNull2, List<StringPos<NULL>> list, NULL aNull3, NULL aNull4, boolean b2) {
			return null;
		}

		@Override
		public NULL pathLength(NULL aNull, NULL pos1, NULL pos2, String s, String s1) {
			return null;
		}

		@Override
		public NULL loadCsvClause(NULL aNull, boolean b, NULL aNull2, NULL aNull3, String s) {
			return null;
		}

		@Override
		public NULL foreachClause(NULL aNull, NULL aNull2, NULL aNull3, List<NULL> list) {
			return null;
		}

		@Override
		public NULL subqueryClause(NULL aNull, NULL aNull2, NULL aNull3) {
			return null;
		}

		@Override
		public NULL subqueryInTransactionsParams(NULL aNull, NULL aNull2) {
			this.hasSeenCallInTx.compareAndSet(false, true);
			return null;
		}

		@Override
		public NULL useGraph(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL hasCatalog(NULL aNull) {
			return null;
		}

		@Override
		public NULL yieldClause(NULL aNull, boolean b, List<NULL> list, NULL pos1, List<NULL> list1, NULL pos2, NULL aNull2, NULL pos3, NULL expression1, NULL pos4, NULL aNull3) {
			return null;
		}

		@Override
		public NULL showIndexClause(NULL aNull, ShowCommandFilterTypes showCommandFilterTypes, boolean b, boolean b1, NULL aNull2, boolean b2) {
			return null;
		}

		@Override
		public NULL showConstraintClause(NULL aNull, ShowCommandFilterTypes showCommandFilterTypes, boolean b, boolean b1, NULL aNull2, boolean b2) {
			return null;
		}

		@Override
		public NULL showProcedureClause(NULL aNull, boolean b, String s, NULL aNull2, boolean b1) {
			return null;
		}

		@Override
		public NULL showFunctionClause(NULL aNull, ShowCommandFilterTypes showCommandFilterTypes, boolean b, String s, NULL aNull2, boolean b1) {
			return null;
		}

		@Override
		public NULL showTransactionsClause(NULL aNull, SimpleEither<List<String>, NULL> simpleEither, NULL aNull2, boolean b) {
			return null;
		}

		@Override
		public NULL terminateTransactionsClause(NULL aNull, SimpleEither<List<String>, NULL> simpleEither) {
			return null;
		}

		@Override
		public NULL createConstraint(NULL aNull, ConstraintType constraintType, boolean b, boolean b1, String s, NULL aNull2, StringPos<NULL> stringPos, List<NULL> list, SimpleEither<Map<String, NULL>, NULL> simpleEither, boolean b2, ConstraintVersion constraintVersion) {
			return null;
		}

		@Override
		public NULL dropConstraint(NULL aNull, String s, boolean b) {
			return null;
		}

		@Override
		public NULL dropConstraint(NULL aNull, ConstraintType constraintType, NULL aNull2, StringPos<NULL> stringPos, List<NULL> list) {
			return null;
		}

		@Override
		public NULL createIndexWithOldSyntax(NULL aNull, StringPos<NULL> stringPos, List<StringPos<NULL>> list) {
			return null;
		}

		@Override
		public NULL createLookupIndex(NULL aNull, boolean b, boolean b1, boolean b2, String s, NULL aNull2, StringPos<NULL> stringPos, NULL variable1, SimpleEither<Map<String, NULL>, NULL> simpleEither) {
			return null;
		}

		@Override
		public NULL createIndex(NULL aNull, boolean b, boolean b1, boolean b2, String s, NULL aNull2, StringPos<NULL> stringPos, List<NULL> list, SimpleEither<Map<String, NULL>, NULL> simpleEither, CreateIndexTypes createIndexTypes) {
			return null;
		}

		@Override
		public NULL createFulltextIndex(NULL aNull, boolean b, boolean b1, boolean b2, String s, NULL aNull2, List<StringPos<NULL>> list, List<NULL> list1, SimpleEither<Map<String, NULL>, NULL> simpleEither) {
			return null;
		}

		@Override
		public NULL dropIndex(NULL aNull, String s, boolean b) {
			return null;
		}

		@Override
		public NULL dropIndex(NULL aNull, StringPos<NULL> stringPos, List<StringPos<NULL>> list) {
			return null;
		}

		@Override
		public NULL createRole(NULL aNull, boolean b, SimpleEither<String, NULL> simpleEither, SimpleEither<String, NULL> simpleEither1, boolean b1) {
			return null;
		}

		@Override
		public NULL dropRole(NULL aNull, SimpleEither<String, NULL> simpleEither, boolean b) {
			return null;
		}

		@Override
		public NULL renameRole(NULL aNull, SimpleEither<String, NULL> simpleEither, SimpleEither<String, NULL> simpleEither1, boolean b) {
			return null;
		}

		@Override
		public NULL showRoles(NULL aNull, boolean b, boolean b1, NULL aNull2, NULL aNull3, NULL aNull4) {
			return null;
		}

		@Override
		public NULL grantRoles(NULL aNull, List<SimpleEither<String, NULL>> list, List<SimpleEither<String, NULL>> list1) {
			return null;
		}

		@Override
		public NULL revokeRoles(NULL aNull, List<SimpleEither<String, NULL>> list, List<SimpleEither<String, NULL>> list1) {
			return null;
		}

		@Override
		public NULL createUser(NULL aNull, boolean b, boolean b1, SimpleEither<String, NULL> simpleEither, NULL aNull2, boolean b2, boolean b3, Boolean aBoolean, SimpleEither<String, NULL> simpleEither1) {
			return null;
		}

		@Override
		public NULL dropUser(NULL aNull, boolean b, SimpleEither<String, NULL> simpleEither) {
			return null;
		}

		@Override
		public NULL renameUser(NULL aNull, SimpleEither<String, NULL> simpleEither, SimpleEither<String, NULL> simpleEither1, boolean b) {
			return null;
		}

		@Override
		public NULL setOwnPassword(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL alterUser(NULL aNull, boolean b, SimpleEither<String, NULL> simpleEither, NULL aNull2, boolean b1, Boolean aBoolean, Boolean aBoolean1, SimpleEither<String, NULL> simpleEither1, boolean b2) {
			return null;
		}

		@Override
		public NULL passwordExpression(NULL aNull) {
			return null;
		}

		@Override
		public NULL passwordExpression(NULL aNull, String s) {
			return null;
		}

		@Override
		public NULL showUsers(NULL aNull, NULL aNull2, NULL aNull3, NULL aNull4) {
			return null;
		}

		@Override
		public NULL showCurrentUser(NULL aNull, NULL aNull2, NULL aNull3, NULL aNull4) {
			return null;
		}

		@Override
		public NULL grantPrivilege(NULL aNull, List<SimpleEither<String, NULL>> list, NULL aNull2) {
			return null;
		}

		@Override
		public NULL denyPrivilege(NULL aNull, List<SimpleEither<String, NULL>> list, NULL aNull2) {
			return null;
		}

		@Override
		public NULL revokePrivilege(NULL aNull, List<SimpleEither<String, NULL>> list, NULL aNull2, boolean b, boolean b1) {
			return null;
		}

		@Override
		public NULL databasePrivilege(NULL aNull, NULL aNull2, List<NULL> list, List<NULL> list1) {
			return null;
		}

		@Override
		public NULL dbmsPrivilege(NULL aNull, NULL aNull2, List<NULL> list) {
			return null;
		}

		@Override
		public NULL graphPrivilege(NULL aNull, NULL aNull2, List<NULL> list, NULL aNull3, List<NULL> list1) {
			return null;
		}

		@Override
		public NULL privilegeAction(ActionType actionType) {
			return null;
		}

		@Override
		public NULL propertiesResource(NULL aNull, List<String> list) {
			return null;
		}

		@Override
		public NULL allPropertiesResource(NULL aNull) {
			return null;
		}

		@Override
		public NULL labelsResource(NULL aNull, List<String> list) {
			return null;
		}

		@Override
		public NULL allLabelsResource(NULL aNull) {
			return null;
		}

		@Override
		public NULL databaseResource(NULL aNull) {
			return null;
		}

		@Override
		public NULL noResource(NULL aNull) {
			return null;
		}

		@Override
		public NULL labelQualifier(NULL aNull, String s) {
			return null;
		}

		@Override
		public NULL relationshipQualifier(NULL aNull, String s) {
			return null;
		}

		@Override
		public NULL elementQualifier(NULL aNull, String s) {
			return null;
		}

		@Override
		public NULL allElementsQualifier(NULL aNull) {
			return null;
		}

		@Override
		public NULL allLabelsQualifier(NULL aNull) {
			return null;
		}

		@Override
		public NULL allRelationshipsQualifier(NULL aNull) {
			return null;
		}

		@Override
		public List<NULL> allQualifier() {
			return null;
		}

		@Override
		public List<NULL> allDatabasesQualifier() {
			return null;
		}

		@Override
		public List<NULL> userQualifier(List<SimpleEither<String, NULL>> list) {
			return null;
		}

		@Override
		public List<NULL> allUsersQualifier() {
			return null;
		}

		@Override
		public List<NULL> graphScopes(NULL aNull, List<SimpleEither<String, NULL>> list, ScopeType scopeType) {
			return null;
		}

		@Override
		public List<NULL> databaseScopes(NULL aNull, List<SimpleEither<String, NULL>> list, ScopeType scopeType) {
			return null;
		}

		@Override
		public NULL createDatabase(NULL aNull, boolean b, SimpleEither<String, NULL> simpleEither, boolean b1, NULL aNull2, SimpleEither<Map<String, NULL>, NULL> simpleEither1) {
			return null;
		}

		@Override
		public NULL dropDatabase(NULL aNull, SimpleEither<String, NULL> simpleEither, boolean b, boolean b1, NULL aNull2) {
			return null;
		}

		@Override
		public NULL alterDatabase(NULL aNull, SimpleEither<String, NULL> simpleEither, boolean b, AccessType accessType) {
			return null;
		}

		@Override
		public NULL showDatabase(NULL aNull, NULL aNull2, NULL aNull3, NULL aNull4, NULL aNull5) {
			return null;
		}

		@Override
		public NULL startDatabase(NULL aNull, SimpleEither<String, NULL> simpleEither, NULL aNull2) {
			return null;
		}

		@Override
		public NULL stopDatabase(NULL aNull, SimpleEither<String, NULL> simpleEither, NULL aNull2) {
			return null;
		}

		@Override
		public NULL databaseScope(NULL aNull, SimpleEither<String, NULL> simpleEither, boolean b, boolean b1) {
			return null;
		}

		@Override
		public NULL wait(boolean b, long l) {
			return null;
		}

		@Override
		public NULL createLocalDatabaseAlias(NULL aNull, boolean b, SimpleEither<String, NULL> simpleEither, SimpleEither<String, NULL> simpleEither1, boolean b1) {
			return null;
		}

		@Override
		public NULL createRemoteDatabaseAlias(NULL aNull, boolean b, SimpleEither<String, NULL> simpleEither, SimpleEither<String, NULL> simpleEither1, boolean b1, SimpleEither<String, NULL> simpleEither2, SimpleEither<String, NULL> simpleEither3, NULL aNull2, SimpleEither<Map<String, NULL>, NULL> simpleEither4) {
			return null;
		}

		@Override
		public NULL alterLocalDatabaseAlias(NULL aNull, SimpleEither<String, NULL> simpleEither, SimpleEither<String, NULL> simpleEither1, boolean b) {
			return null;
		}

		@Override
		public NULL alterRemoteDatabaseAlias(NULL aNull, SimpleEither<String, NULL> simpleEither, SimpleEither<String, NULL> simpleEither1, boolean b, SimpleEither<String, NULL> simpleEither2, SimpleEither<String, NULL> simpleEither3, NULL aNull2, SimpleEither<Map<String, NULL>, NULL> simpleEither4) {
			return null;
		}

		@Override
		public NULL dropAlias(NULL aNull, SimpleEither<String, NULL> simpleEither, boolean b) {
			return null;
		}

		@Override
		public NULL showAliases(NULL aNull, NULL aNull2, NULL aNull3, NULL aNull4) {
			return null;
		}

		@Override
		public NULL newVariable(NULL aNull, String s) {
			return null;
		}

		@Override
		public NULL newParameter(NULL aNull, NULL aNull2, ParameterType parameterType) {
			return null;
		}

		@Override
		public NULL newParameter(NULL aNull, String s, ParameterType parameterType) {
			return null;
		}

		@Override
		public NULL newSensitiveStringParameter(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL newSensitiveStringParameter(NULL aNull, String s) {
			return null;
		}

		@Override
		public NULL oldParameter(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL newDouble(NULL aNull, String s) {
			return null;
		}

		@Override
		public NULL newDecimalInteger(NULL aNull, String s, boolean b) {
			return null;
		}

		@Override
		public NULL newHexInteger(NULL aNull, String s, boolean b) {
			return null;
		}

		@Override
		public NULL newOctalInteger(NULL aNull, String s, boolean b) {
			return null;
		}

		@Override
		public NULL newString(NULL aNull, String s) {
			return null;
		}

		@Override
		public NULL newTrueLiteral(NULL aNull) {
			return null;
		}

		@Override
		public NULL newFalseLiteral(NULL aNull) {
			return null;
		}

		@Override
		public NULL newNullLiteral(NULL aNull) {
			return null;
		}

		@Override
		public NULL listLiteral(NULL aNull, List<NULL> list) {
			return null;
		}

		@Override
		public NULL mapLiteral(NULL aNull, List<StringPos<NULL>> list, List<NULL> list1) {
			return null;
		}

		@Override
		public NULL hasLabelsOrTypes(NULL aNull, List<StringPos<NULL>> list) {
			return null;
		}

		@Override
		public NULL property(NULL aNull, StringPos<NULL> stringPos) {
			return null;
		}

		@Override
		public NULL or(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL xor(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL and(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL ands(List<NULL> list) {
			return null;
		}

		@Override
		public NULL not(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL plus(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL minus(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL multiply(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL divide(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL modulo(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL pow(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL unaryPlus(NULL aNull) {
			return null;
		}

		@Override
		public NULL unaryPlus(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL unaryMinus(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL eq(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL neq(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL neq2(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL lte(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL gte(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL lt(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL gt(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL regeq(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL startsWith(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL endsWith(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL contains(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL in(NULL aNull, NULL aNull2, NULL expression1) {
			return null;
		}

		@Override
		public NULL isNull(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL isNotNull(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL listLookup(NULL aNull, NULL expression1) {
			return null;
		}

		@Override
		public NULL listSlice(NULL aNull, NULL aNull2, NULL expression1, NULL expression2) {
			return null;
		}

		@Override
		public NULL newCountStar(NULL aNull) {
			return null;
		}

		@Override
		public NULL functionInvocation(NULL aNull, NULL pos1, List<String> list, String s, boolean b, List<NULL> list1) {
			return null;
		}

		@Override
		public NULL listComprehension(NULL aNull, NULL aNull2, NULL aNull3, NULL expression1, NULL expression2) {
			return null;
		}

		@Override
		public NULL patternComprehension(NULL aNull, NULL pos1, NULL aNull2, NULL aNull3, NULL aNull4, NULL expression1) {
			return null;
		}

		@Override
		public NULL filterExpression(NULL aNull, NULL aNull2, NULL aNull3, NULL expression1) {
			return null;
		}

		@Override
		public NULL extractExpression(NULL aNull, NULL aNull2, NULL aNull3, NULL expression1, NULL expression2) {
			return null;
		}

		@Override
		public NULL reduceExpression(NULL aNull, NULL aNull2, NULL aNull3, NULL variable1, NULL expression1, NULL expression2) {
			return null;
		}

		@Override
		public NULL allExpression(NULL aNull, NULL aNull2, NULL aNull3, NULL expression1) {
			return null;
		}

		@Override
		public NULL anyExpression(NULL aNull, NULL aNull2, NULL aNull3, NULL expression1) {
			return null;
		}

		@Override
		public NULL noneExpression(NULL aNull, NULL aNull2, NULL aNull3, NULL expression1) {
			return null;
		}

		@Override
		public NULL singleExpression(NULL aNull, NULL aNull2, NULL aNull3, NULL expression1) {
			return null;
		}

		@Override
		public NULL patternExpression(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL existsSubQuery(NULL aNull, List<NULL> list, NULL aNull2) {
			return null;
		}

		@Override
		public NULL mapProjection(NULL aNull, NULL aNull2, List<NULL> list) {
			return null;
		}

		@Override
		public NULL mapProjectionLiteralEntry(StringPos<NULL> stringPos, NULL aNull) {
			return null;
		}

		@Override
		public NULL mapProjectionProperty(StringPos<NULL> stringPos) {
			return null;
		}

		@Override
		public NULL mapProjectionVariable(NULL aNull) {
			return null;
		}

		@Override
		public NULL mapProjectionAll(NULL aNull) {
			return null;
		}

		@Override
		public NULL caseExpression(NULL aNull, NULL aNull2, List<NULL> list, List<NULL> list1, NULL expression1) {
			return null;
		}

		@Override
		public NULL inputPosition(int i, int i1, int i2) {
			return null;
		}
	}
}
