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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.neo4j.cypher.internal.ast.factory.ASTExceptionFactory;
import org.neo4j.cypher.internal.ast.factory.ASTFactory;
import org.neo4j.cypher.internal.ast.factory.ASTFactory.NULL;
import org.neo4j.cypher.internal.ast.factory.AccessType;
import org.neo4j.cypher.internal.ast.factory.ActionType;
import org.neo4j.cypher.internal.ast.factory.CallInTxsOnErrorBehaviourType;
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
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactivestreams.ReactiveSession;

import reactor.core.publisher.Mono;

/**
 * Uses the AST factory basically as a visitor and memorizes if any call {} in transactions of element has been seen.
 *
 * @author Michael J. Simons
 */
abstract class AbstractQueryEvaluator implements QueryEvaluator {

	private static final Pattern CALL_PATTERN = Pattern.compile("(?ims)(?<!`)([^`\\s*]\\s*+CALL\\s*\\{.*}\\s*IN\\s+TRANSACTIONS)(?!`)");

	protected final Driver driver;
	private final Mono<Boolean> enterpriseEdition;

	AbstractQueryEvaluator(Driver driver) {
		this.driver = driver;
		this.enterpriseEdition = Mono.usingWhen(
				Mono.fromCallable(() -> driver.session(ReactiveSession.class)),
				rxSession -> Mono.fromDirect(rxSession.run("CALL dbms.components() YIELD edition RETURN toLower(edition) = 'enterprise'")).flatMap(rs -> Mono.fromDirect(rs.records())).map(record -> record.get(0).asBoolean()),
				ReactiveSession::close
		).cache();
	}

	@Override
	public final Mono<Boolean> isEnterpriseEdition() {
		return enterpriseEdition;
	}

	/**
	 * Computes or retrieves the transaction mode required by the query.
	 *
	 * @param query The string value of a query to be executed, must not be {@literal null} or blank
	 * @return The transaction mode required by the query
	 */
	protected final Mono<TransactionMode> getTransactionMode(String query) {

		var result = TransactionMode.MANAGED;
		if (CALL_PATTERN.matcher(query).find()) {
			var characteristics = AbstractQueryEvaluator.getCharacteristics(query);
			result = characteristics.callInTx() ? TransactionMode.IMPLICIT : TransactionMode.MANAGED;
		}

		return Mono.just(result);
	}

	private record QueryCharacteristics(boolean callInTx) {
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
			return new QueryCharacteristics(astFactory.hasSeenCallInTx.get());
		} catch (Exception e) {
			return new QueryCharacteristics(false);
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

		@Override
		public NULL newSingleQuery(NULL p, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL newSingleQuery(List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL newUnion(NULL p, NULL lhs, NULL rhs, boolean all) {
			return null;
		}

		@Override
		public NULL useClause(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL newReturnClause(NULL p, boolean distinct, NULL aNull, List<NULL> order, NULL orderPos, NULL skip, NULL skipPosition, NULL limit, NULL limitPosition) {
			return null;
		}

		@Override
		public NULL newReturnItems(NULL p, boolean returnAll, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL newReturnItem(NULL p, NULL e, NULL v) {
			return null;
		}

		@Override
		public NULL newReturnItem(NULL p, NULL e, int eStartOffset, int eEndOffset) {
			return null;
		}

		@Override
		public NULL orderDesc(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL orderAsc(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL whereClause(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL withClause(NULL p, NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL matchClause(NULL p, boolean optional, List<NULL> nulls, NULL patternPos, List<NULL> nulls2, NULL aNull) {
			return null;
		}

		@Override
		public NULL usingIndexHint(NULL p, NULL v, String labelOrRelType, List<String> properties, boolean seekOnly, HintIndexType indexType) {
			return null;
		}

		@Override
		public NULL usingJoin(NULL p, List<NULL> joinVariables) {
			return null;
		}

		@Override
		public NULL usingScan(NULL p, NULL v, String labelOrRelType) {
			return null;
		}

		@Override
		public NULL createClause(NULL p, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL setClause(NULL p, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL setProperty(NULL aNull, NULL value) {
			return null;
		}

		@Override
		public NULL setVariable(NULL aNull, NULL value) {
			return null;
		}

		@Override
		public NULL addAndSetVariable(NULL aNull, NULL value) {
			return null;
		}

		@Override
		public NULL setLabels(NULL aNull, List<StringPos<NULL>> value) {
			return null;
		}

		@Override
		public NULL removeClause(NULL p, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL removeProperty(NULL aNull) {
			return null;
		}

		@Override
		public NULL removeLabels(NULL aNull, List<StringPos<NULL>> labels) {
			return null;
		}

		@Override
		public NULL deleteClause(NULL p, boolean detach, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL unwindClause(NULL p, NULL e, NULL v) {
			return null;
		}

		@Override
		public NULL mergeClause(NULL p, NULL aNull, List<NULL> nulls, List<MergeActionType> actionTypes, List<NULL> positions) {
			return null;
		}

		@Override
		public NULL callClause(NULL p, NULL namespacePosition, NULL procedureNamePosition, NULL procedureResultPosition, List<String> namespace, String name, List<NULL> arguments, boolean yieldAll, List<NULL> nulls, NULL aNull) {
			return null;
		}

		@Override
		public NULL callResultItem(NULL p, String name, NULL v) {
			return null;
		}

		@Override
		public NULL namedPattern(NULL v, NULL aNull) {
			return null;
		}

		@Override
		public NULL shortestPathPattern(NULL p, NULL aNull) {
			return null;
		}

		@Override
		public NULL allShortestPathsPattern(NULL p, NULL aNull) {
			return null;
		}

		@Override
		public NULL everyPathPattern(List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL nodePattern(NULL p, NULL v, NULL aNull, NULL properties, NULL predicate) {
			return null;
		}

		@Override
		public NULL relationshipPattern(NULL p, boolean left, boolean right, NULL v, NULL aNull, NULL aNull2, NULL properties, NULL predicate) {
			return null;
		}

		@Override
		public NULL pathLength(NULL p, NULL pMin, NULL pMax, String minLength, String maxLength) {
			return null;
		}

		@Override
		public NULL intervalPathQuantifier(NULL p, NULL posLowerBound, NULL posUpperBound, String lowerBound, String upperBound) {
			return null;
		}

		@Override
		public NULL fixedPathQuantifier(NULL p, NULL valuePos, String value) {
			return null;
		}

		@Override
		public NULL plusPathQuantifier(NULL p) {
			return null;
		}

		@Override
		public NULL starPathQuantifier(NULL p) {
			return null;
		}

		@Override
		public NULL parenthesizedPathPattern(NULL p, NULL internalPattern, NULL where, NULL aNull) {
			return null;
		}

		@Override
		public NULL quantifiedRelationship(NULL rel, NULL aNull) {
			return null;
		}

		@Override
		public NULL loadCsvClause(NULL p, boolean headers, NULL source, NULL v, String fieldTerminator) {
			return null;
		}

		@Override
		public NULL foreachClause(NULL p, NULL v, NULL list, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL subqueryClause(NULL p, NULL subquery, NULL inTransactions) {
			return null;
		}

		@Override
		public NULL subqueryInTransactionsParams(NULL p, NULL batchParams, NULL errorParams, NULL reportParams) {
			hasSeenCallInTx.set(true);
			return null;
		}

		@Override
		public NULL subqueryInTransactionsBatchParameters(NULL p, NULL batchSize) {
			return null;
		}

		@Override
		public NULL subqueryInTransactionsErrorParameters(NULL p, CallInTxsOnErrorBehaviourType onErrorBehaviour) {
			return null;
		}

		@Override
		public NULL subqueryInTransactionsReportParameters(NULL p, NULL v) {
			return null;
		}

		@Override
		public NULL useGraph(NULL aNull, NULL aNull2) {
			return null;
		}

		@Override
		public NULL yieldClause(NULL p, boolean returnAll, List<NULL> nulls, NULL returnItemsPosition, List<NULL> orderBy, NULL orderPos, NULL skip, NULL skipPosition, NULL limit, NULL limitPosition, NULL aNull) {
			return null;
		}

		@Override
		public NULL showIndexClause(NULL p, ShowCommandFilterTypes indexType, boolean brief, boolean verbose, NULL aNull, boolean hasYield) {
			return null;
		}

		@Override
		public NULL showConstraintClause(NULL p, ShowCommandFilterTypes constraintType, boolean brief, boolean verbose, NULL aNull, boolean hasYield) {
			return null;
		}

		@Override
		public NULL showProcedureClause(NULL p, boolean currentUser, String user, NULL aNull, boolean hasYield) {
			return null;
		}

		@Override
		public NULL showFunctionClause(NULL p, ShowCommandFilterTypes functionType, boolean currentUser, String user, NULL aNull, boolean hasYield) {
			return null;
		}

		@Override
		public NULL showTransactionsClause(NULL p, SimpleEither<List<String>, NULL> ids, NULL aNull, NULL yieldClause) {
			return null;
		}

		@Override
		public NULL terminateTransactionsClause(NULL p, SimpleEither<List<String>, NULL> ids, NULL aNull, NULL yieldClause) {
			return null;
		}

		@Override
		public NULL turnYieldToWith(NULL yieldClause) {
			return null;
		}

		@Override
		public NULL showSettingsClause(NULL p, SimpleEither<List<String>, NULL> names, NULL aNull, boolean hasYield) {
			return null;
		}

		@Override
		public NULL createConstraint(NULL p, ConstraintType constraintType, boolean replace, boolean ifNotExists, String constraintName, NULL aNull, StringPos<NULL> label, List<NULL> nulls, SimpleEither<Map<String, NULL>, NULL> options, boolean containsOn, ConstraintVersion constraintVersion) {
			return null;
		}

		@Override
		public NULL dropConstraint(NULL p, String name, boolean ifExists) {
			return null;
		}

		@Override
		public NULL dropConstraint(NULL p, ConstraintType constraintType, NULL aNull, StringPos<NULL> label, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL createIndexWithOldSyntax(NULL p, StringPos<NULL> label, List<StringPos<NULL>> properties) {
			return null;
		}

		@Override
		public NULL createLookupIndex(NULL p, boolean replace, boolean ifNotExists, boolean isNode, String indexName, NULL aNull, StringPos<NULL> functionName, NULL functionParameter, SimpleEither<Map<String, NULL>, NULL> options) {
			return null;
		}

		@Override
		public NULL createIndex(NULL p, boolean replace, boolean ifNotExists, boolean isNode, String indexName, NULL aNull, StringPos<NULL> label, List<NULL> nulls, SimpleEither<Map<String, NULL>, NULL> options, CreateIndexTypes indexType) {
			return null;
		}

		@Override
		public NULL createFulltextIndex(NULL p, boolean replace, boolean ifNotExists, boolean isNode, String indexName, NULL aNull, List<StringPos<NULL>> labels, List<NULL> nulls, SimpleEither<Map<String, NULL>, NULL> options) {
			return null;
		}

		@Override
		public NULL dropIndex(NULL p, String name, boolean ifExists) {
			return null;
		}

		@Override
		public NULL dropIndex(NULL p, StringPos<NULL> label, List<StringPos<NULL>> propertyNames) {
			return null;
		}

		@Override
		public NULL createRole(NULL p, boolean replace, SimpleEither<String, NULL> roleName, SimpleEither<String, NULL> fromRole, boolean ifNotExists) {
			return null;
		}

		@Override
		public NULL dropRole(NULL p, SimpleEither<String, NULL> roleName, boolean ifExists) {
			return null;
		}

		@Override
		public NULL renameRole(NULL p, SimpleEither<String, NULL> fromRoleName, SimpleEither<String, NULL> toRoleName, boolean ifExists) {
			return null;
		}

		@Override
		public NULL showRoles(NULL p, boolean withUsers, boolean showAll, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull) {
			return null;
		}

		@Override
		public NULL grantRoles(NULL p, List<SimpleEither<String, NULL>> roles, List<SimpleEither<String, NULL>> users) {
			return null;
		}

		@Override
		public NULL revokeRoles(NULL p, List<SimpleEither<String, NULL>> roles, List<SimpleEither<String, NULL>> users) {
			return null;
		}

		@Override
		public NULL createUser(NULL p, boolean replace, boolean ifNotExists, SimpleEither<String, NULL> username, NULL password, boolean encrypted, boolean changeRequired, Boolean suspended, NULL homeDatabase) {
			return null;
		}

		@Override
		public NULL dropUser(NULL p, boolean ifExists, SimpleEither<String, NULL> username) {
			return null;
		}

		@Override
		public NULL renameUser(NULL p, SimpleEither<String, NULL> fromUserName, SimpleEither<String, NULL> toUserName, boolean ifExists) {
			return null;
		}

		@Override
		public NULL setOwnPassword(NULL p, NULL currentPassword, NULL newPassword) {
			return null;
		}

		@Override
		public NULL alterUser(NULL p, boolean ifExists, SimpleEither<String, NULL> username, NULL password, boolean encrypted, Boolean changeRequired, Boolean suspended, NULL homeDatabase, boolean removeHome) {
			return null;
		}

		@Override
		public NULL passwordExpression(NULL password) {
			return null;
		}

		@Override
		public NULL passwordExpression(NULL p, String password) {
			return null;
		}

		@Override
		public NULL showUsers(NULL p, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull) {
			return null;
		}

		@Override
		public NULL showCurrentUser(NULL p, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull) {
			return null;
		}

		@Override
		public NULL showAllPrivileges(NULL p, boolean asCommand, boolean asRevoke, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull) {
			return null;
		}

		@Override
		public NULL showRolePrivileges(NULL p, List<SimpleEither<String, NULL>> roles, boolean asCommand, boolean asRevoke, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull) {
			return null;
		}

		@Override
		public NULL showUserPrivileges(NULL p, List<SimpleEither<String, NULL>> users, boolean asCommand, boolean asRevoke, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull) {
			return null;
		}

		@Override
		public NULL grantPrivilege(NULL p, List<SimpleEither<String, NULL>> roles, NULL privilege) {
			return null;
		}

		@Override
		public NULL denyPrivilege(NULL p, List<SimpleEither<String, NULL>> roles, NULL privilege) {
			return null;
		}

		@Override
		public NULL revokePrivilege(NULL p, List<SimpleEither<String, NULL>> roles, NULL privilege, boolean revokeGrant, boolean revokeDeny) {
			return null;
		}

		@Override
		public NULL databasePrivilege(NULL p, NULL aNull, List<NULL> scope, List<NULL> qualifier, boolean immutable) {
			return null;
		}

		@Override
		public NULL dbmsPrivilege(NULL p, NULL aNull, List<NULL> qualifier, boolean immutable) {
			return null;
		}

		@Override
		public NULL graphPrivilege(NULL p, NULL aNull, List<NULL> scope, NULL aNull2, List<NULL> qualifier, boolean immutable) {
			return null;
		}

		@Override
		public NULL privilegeAction(ActionType action) {
			return null;
		}

		@Override
		public NULL propertiesResource(NULL p, List<String> property) {
			return null;
		}

		@Override
		public NULL allPropertiesResource(NULL p) {
			return null;
		}

		@Override
		public NULL labelsResource(NULL p, List<String> label) {
			return null;
		}

		@Override
		public NULL allLabelsResource(NULL p) {
			return null;
		}

		@Override
		public NULL databaseResource(NULL p) {
			return null;
		}

		@Override
		public NULL noResource(NULL p) {
			return null;
		}

		@Override
		public NULL labelQualifier(NULL p, String label) {
			return null;
		}

		@Override
		public NULL relationshipQualifier(NULL p, String relationshipType) {
			return null;
		}

		@Override
		public NULL elementQualifier(NULL p, String name) {
			return null;
		}

		@Override
		public NULL allElementsQualifier(NULL p) {
			return null;
		}

		@Override
		public NULL allLabelsQualifier(NULL p) {
			return null;
		}

		@Override
		public NULL allRelationshipsQualifier(NULL p) {
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
		public List<NULL> userQualifier(List<SimpleEither<String, NULL>> users) {
			return null;
		}

		@Override
		public List<NULL> allUsersQualifier() {
			return null;
		}

		@Override
		public List<NULL> functionQualifier(NULL p, List<String> functions) {
			return null;
		}

		@Override
		public List<NULL> procedureQualifier(NULL p, List<String> procedures) {
			return null;
		}

		@Override
		public List<NULL> settingQualifier(NULL p, List<String> names) {
			return null;
		}

		@Override
		public List<NULL> graphScopes(NULL p, List<NULL> graphNames, ScopeType scopeType) {
			return null;
		}

		@Override
		public List<NULL> databaseScopes(NULL p, List<NULL> nulls, ScopeType scopeType) {
			return null;
		}

		@Override
		public NULL enableServer(NULL p, SimpleEither<String, NULL> serverName, SimpleEither<Map<String, NULL>, NULL> options) {
			return null;
		}

		@Override
		public NULL alterServer(NULL p, SimpleEither<String, NULL> serverName, SimpleEither<Map<String, NULL>, NULL> options) {
			return null;
		}

		@Override
		public NULL renameServer(NULL p, SimpleEither<String, NULL> serverName, SimpleEither<String, NULL> newName) {
			return null;
		}

		@Override
		public NULL dropServer(NULL p, SimpleEither<String, NULL> serverName) {
			return null;
		}

		@Override
		public NULL showServers(NULL p, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull) {
			return null;
		}

		@Override
		public NULL deallocateServers(NULL p, boolean dryRun, List<SimpleEither<String, NULL>> serverNames) {
			return null;
		}

		@Override
		public NULL reallocateDatabases(NULL p, boolean dryRun) {
			return null;
		}

		@Override
		public NULL createDatabase(NULL p, boolean replace, NULL aNull, boolean ifNotExists, NULL aNull2, SimpleEither<Map<String, NULL>, NULL> options, Integer topologyPrimaries, Integer topologySecondaries) {
			return null;
		}

		@Override
		public NULL createCompositeDatabase(NULL p, boolean replace, NULL compositeDatabaseName, boolean ifNotExists, SimpleEither<Map<String, NULL>, NULL> options, NULL aNull) {
			return null;
		}

		@Override
		public NULL dropDatabase(NULL p, NULL aNull, boolean ifExists, boolean composite, boolean dumpData, NULL wait) {
			return null;
		}

		@Override
		public NULL alterDatabase(NULL p, NULL aNull, boolean ifExists, AccessType accessType, Integer topologyPrimaries, Integer topologySecondaries, Map<String, NULL> options, Set<String> optionsToRemove) {
			return null;
		}

		@Override
		public NULL showDatabase(NULL p, NULL aNull, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull2) {
			return null;
		}

		@Override
		public NULL startDatabase(NULL p, NULL aNull, NULL wait) {
			return null;
		}

		@Override
		public NULL stopDatabase(NULL p, NULL aNull, NULL wait) {
			return null;
		}

		@Override
		public NULL databaseScope(NULL p, NULL aNull, boolean isDefault, boolean isHome) {
			return null;
		}

		@Override
		public NULL wait(boolean wait, long seconds) {
			return null;
		}

		@Override
		public NULL databaseName(NULL p, List<String> names) {
			return null;
		}

		@Override
		public NULL databaseName(NULL param) {
			return null;
		}

		@Override
		public NULL createLocalDatabaseAlias(NULL p, boolean replace, NULL aliasName, NULL targetName, boolean ifNotExists, SimpleEither<Map<String, NULL>, NULL> properties) {
			return null;
		}

		@Override
		public NULL createRemoteDatabaseAlias(NULL p, boolean replace, NULL aliasName, NULL targetName, boolean ifNotExists, SimpleEither<String, NULL> url, SimpleEither<String, NULL> username, NULL password, SimpleEither<Map<String, NULL>, NULL> driverSettings, SimpleEither<Map<String, NULL>, NULL> properties) {
			return null;
		}

		@Override
		public NULL alterLocalDatabaseAlias(NULL p, NULL aliasName, NULL targetName, boolean ifExists, SimpleEither<Map<String, NULL>, NULL> properties) {
			return null;
		}

		@Override
		public NULL alterRemoteDatabaseAlias(NULL p, NULL aliasName, NULL targetName, boolean ifExists, SimpleEither<String, NULL> url, SimpleEither<String, NULL> username, NULL password, SimpleEither<Map<String, NULL>, NULL> driverSettings, SimpleEither<Map<String, NULL>, NULL> properties) {
			return null;
		}

		@Override
		public NULL dropAlias(NULL p, NULL aliasName, boolean ifExists) {
			return null;
		}

		@Override
		public NULL showAliases(NULL p, NULL aliasName, NULL yieldExpr, NULL returnWithoutGraph, NULL aNull) {
			return null;
		}

		@Override
		public NULL newVariable(NULL p, String name) {
			return null;
		}

		@Override
		public NULL newParameter(NULL p, NULL v, ParameterType type) {
			return null;
		}

		@Override
		public NULL newParameter(NULL p, String offset, ParameterType type) {
			return null;
		}

		@Override
		public NULL newSensitiveStringParameter(NULL p, NULL v) {
			return null;
		}

		@Override
		public NULL newSensitiveStringParameter(NULL p, String offset) {
			return null;
		}

		@Override
		public NULL newDouble(NULL p, String image) {
			return null;
		}

		@Override
		public NULL newDecimalInteger(NULL p, String image, boolean negated) {
			return null;
		}

		@Override
		public NULL newHexInteger(NULL p, String image, boolean negated) {
			return null;
		}

		@Override
		public NULL newOctalInteger(NULL p, String image, boolean negated) {
			return null;
		}

		@Override
		public NULL newString(NULL p, String image) {
			return null;
		}

		@Override
		public NULL newTrueLiteral(NULL p) {
			return null;
		}

		@Override
		public NULL newFalseLiteral(NULL p) {
			return null;
		}

		@Override
		public NULL newInfinityLiteral(NULL p) {
			return null;
		}

		@Override
		public NULL newNaNLiteral(NULL p) {
			return null;
		}

		@Override
		public NULL newNullLiteral(NULL p) {
			return null;
		}

		@Override
		public NULL listLiteral(NULL p, List<NULL> values) {
			return null;
		}

		@Override
		public NULL mapLiteral(NULL p, List<StringPos<NULL>> keys, List<NULL> values) {
			return null;
		}

		@Override
		public NULL property(NULL subject, StringPos<NULL> propertyKeyName) {
			return null;
		}

		@Override
		public NULL or(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL xor(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL and(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL labelConjunction(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL labelDisjunction(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL labelNegation(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL labelWildcard(NULL p) {
			return null;
		}

		@Override
		public NULL labelLeaf(NULL p, String e, NULL aNull) {
			return null;
		}

		@Override
		public NULL labelColonConjunction(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL labelColonDisjunction(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL labelExpressionPredicate(NULL subject, NULL exp) {
			return null;
		}

		@Override
		public NULL ands(List<NULL> exprs) {
			return null;
		}

		@Override
		public NULL not(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL plus(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL minus(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL multiply(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL divide(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL modulo(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL pow(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL unaryPlus(NULL e) {
			return null;
		}

		@Override
		public NULL unaryPlus(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL unaryMinus(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL eq(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL neq(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL neq2(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL lte(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL gte(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL lt(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL gt(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL regeq(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL startsWith(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL endsWith(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL contains(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL in(NULL p, NULL lhs, NULL rhs) {
			return null;
		}

		@Override
		public NULL isNull(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL isNotNull(NULL p, NULL e) {
			return null;
		}

		@Override
		public NULL listLookup(NULL list, NULL index) {
			return null;
		}

		@Override
		public NULL listSlice(NULL p, NULL list, NULL start, NULL end) {
			return null;
		}

		@Override
		public NULL newCountStar(NULL p) {
			return null;
		}

		@Override
		public NULL functionInvocation(NULL p, NULL functionNamePosition, List<String> namespace, String name, boolean distinct, List<NULL> arguments) {
			return null;
		}

		@Override
		public NULL listComprehension(NULL p, NULL v, NULL list, NULL where, NULL projection) {
			return null;
		}

		@Override
		public NULL patternComprehension(NULL p, NULL relationshipPatternPosition, NULL v, NULL aNull, NULL where, NULL projection) {
			return null;
		}

		@Override
		public NULL reduceExpression(NULL p, NULL acc, NULL accExpr, NULL v, NULL list, NULL innerExpr) {
			return null;
		}

		@Override
		public NULL allExpression(NULL p, NULL v, NULL list, NULL where) {
			return null;
		}

		@Override
		public NULL anyExpression(NULL p, NULL v, NULL list, NULL where) {
			return null;
		}

		@Override
		public NULL noneExpression(NULL p, NULL v, NULL list, NULL where) {
			return null;
		}

		@Override
		public NULL singleExpression(NULL p, NULL v, NULL list, NULL where) {
			return null;
		}

		@Override
		public NULL patternExpression(NULL p, NULL aNull) {
			return null;
		}

		@Override
		public NULL existsExpression(NULL p, List<NULL> nulls, NULL q, NULL aNull) {
			return null;
		}

		@Override
		public NULL countExpression(NULL p, List<NULL> nulls, NULL q, NULL aNull) {
			return null;
		}

		@Override
		public NULL collectExpression(NULL p, NULL q) {
			return null;
		}

		@Override
		public NULL mapProjection(NULL p, NULL v, List<NULL> nulls) {
			return null;
		}

		@Override
		public NULL mapProjectionLiteralEntry(StringPos<NULL> property, NULL value) {
			return null;
		}

		@Override
		public NULL mapProjectionProperty(StringPos<NULL> property) {
			return null;
		}

		@Override
		public NULL mapProjectionVariable(NULL v) {
			return null;
		}

		@Override
		public NULL mapProjectionAll(NULL p) {
			return null;
		}

		@Override
		public NULL caseExpression(NULL p, NULL e, List<NULL> whens, List<NULL> thens, NULL elze) {
			return null;
		}

		@Override
		public NULL inputPosition(int offset, int line, int column) {
			return null;
		}

		@Override
		public NULL nodeType() {
			return null;
		}

		@Override
		public NULL relationshipType() {
			return null;
		}

		@Override
		public NULL nodeOrRelationshipType() {
			return null;
		}
	}
}
