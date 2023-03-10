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

import java.util.Arrays;
import java.util.function.Function;

import org.neo4j.driver.AccessMode;
import org.neo4j.driver.BookmarkManager;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.reactivestreams.ReactiveQueryRunner;
import org.neo4j.driver.reactivestreams.ReactiveResult;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.http.config.ApplicationProperties;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

/**
 * @author Michael J. Simons
 */
@Service
class DefaultNeo4jAdapter implements Neo4jAdapter {

	private final ApplicationProperties applicationProperties;

	private final QueryEvaluator queryEvaluator;

	private final Driver driver;

	private final BookmarkManager bookmarkManager;

	DefaultNeo4jAdapter(ApplicationProperties applicationProperties, QueryEvaluator queryEvaluator, Driver driver, BookmarkManager bookmarkManager) {
		this.applicationProperties = applicationProperties;
		this.queryEvaluator = queryEvaluator;
		this.driver = driver;
		this.bookmarkManager = bookmarkManager;
	}

	@Override
	public Flux<Record> stream(Neo4jPrincipal principal, String database, Query query) {

		return queryEvaluator.getExecutionRequirements(principal, query.text())
			.flatMapMany(requirements -> this.execute0(principal, database, requirements, q -> Mono.fromDirect(q.run(query)).flatMapMany(ReactiveResult::records)));
	}

	@Override
	public Mono<ResultContainer> run(Neo4jPrincipal principal, String database, AnnotatedQuery query, AnnotatedQuery... additionalQueries) {

		Flux<AnnotatedQuery> queries = Flux.just(query);
		if (additionalQueries != null && additionalQueries.length > 0) {
			queries = queries.concatWith(Flux.fromStream(Arrays.stream(additionalQueries)));
		}

		record ResultAndSummary(EagerResult result, ResultSummary summary) {
		}

		return queries.flatMapSequential(theQuery -> Mono.just(theQuery).zipWith(queryEvaluator.getExecutionRequirements(principal, theQuery.text()))
				.flatMap(q -> Mono.fromDirect(this.execute0(principal, database, q.getT2(), runner -> {
					var annotatedQuery = q.getT1();
					var rxResult = runner.run(annotatedQuery.value());
					return Mono.fromDirect(rxResult)
						.flatMap(reactiveResult -> Mono.just(reactiveResult.keys())
							.zipWith(Flux.from(reactiveResult.records()).collectList())
							.flatMap(v -> Mono.just(v).zipWith(Mono.fromDirect(reactiveResult.consume()), (t, s) -> Tuples.of(t.getT1(), t.getT2(), s))))
						.map(content -> new ResultAndSummary(EagerResult.success(content, annotatedQuery.includeStats(), annotatedQuery.resultDataContents()), content.getT3()));
				}))).onErrorResume(Neo4jException.class, e -> Mono.just(new ResultAndSummary(EagerResult.error(e), null)))
			)
			.collect(ResultContainer::new, (container, element) -> {
				if (element.result().isError()) {
					container.errors.add(element.result().exception());
				} else {
					container.results.add(element.result());
					container.notifications.addAll(element.summary().notifications());
				}
			});
	}

	<T> Publisher<T> execute0(Neo4jPrincipal principal, String database, QueryEvaluator.ExecutionRequirements requirements, Function<ReactiveQueryRunner, Publisher<T>> query) {

		var sessionSupplier = queryEvaluator.isEnterpriseEdition().
			flatMap(v -> {
				var builder = v ? SessionConfig.builder().withImpersonatedUser(principal.username()) : SessionConfig.builder();
				var sessionConfig = builder
					.withBookmarkManager(bookmarkManager)
					.withDatabase(database)
					.withDefaultAccessMode(requirements.target() == QueryEvaluator.Target.WRITERS ? AccessMode.WRITE : AccessMode.READ)
					.build();
				return Mono.fromCallable(() -> driver.session(ReactiveSession.class, sessionConfig));
			});

		Flux<T> flow;
		if (requirements.transactionMode() == QueryEvaluator.TransactionMode.IMPLICIT) {
			flow = Flux.usingWhen(sessionSupplier, query, ReactiveSession::close);
		} else {
			flow = switch (requirements.target()) {
				case AUTO, WRITERS -> Flux.usingWhen(
					sessionSupplier,
					session -> session.executeWrite(query::apply),
					ReactiveSession::close
				);
				case READERS -> Flux.usingWhen(
					sessionSupplier,
					session -> session.executeRead(query::apply),
					ReactiveSession::close
				);
			};
		}
		return flow.limitRate(applicationProperties.fetchSize(), applicationProperties.fetchSize() / 2);
	}
}
