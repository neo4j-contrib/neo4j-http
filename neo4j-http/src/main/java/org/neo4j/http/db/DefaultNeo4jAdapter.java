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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.reactive.RxQueryRunner;
import org.neo4j.driver.reactive.RxSession;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.http.config.ApplicationProperties;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * @author Michael J. Simons
 */
@Service
@Primary
class DefaultNeo4jAdapter implements Neo4jAdapter {

	private final ApplicationProperties applicationProperties;

	private final QueryEvaluator queryEvaluator;

	private final Driver driver;

	DefaultNeo4jAdapter(ApplicationProperties applicationProperties, QueryEvaluator queryEvaluator, Driver driver) {
		this.applicationProperties = applicationProperties;
		this.queryEvaluator = queryEvaluator;
		this.driver = driver;
	}

	String normalizeQuery(String query) {
		return Optional.ofNullable(query).map(String::trim).filter(Predicate.not(String::isBlank)).orElseThrow();
	}

	@Override
	@SuppressWarnings({"deprecation", "RedundantSuppression"})
	public Flux<Record> stream(Neo4jPrincipal principal, String query) {

		var theQuery = normalizeQuery(query);
		return Mono.just(principal)
			.zipWith(queryEvaluator.getExecutionRequirements(principal, theQuery))
			.flatMapMany(env -> this.execute0(env, q -> Flux.from(q.run(query).records())));
	}

	@Override
	@SuppressWarnings({"deprecation", "RedundantSuppression"})
	public Mono<ResultContainer> run(Neo4jPrincipal principal, AnnotatedQuery query, AnnotatedQuery... additionalQueries) {

		Flux<AnnotatedQuery> queries = Flux.just(query);
		if (additionalQueries != null && additionalQueries.length > 0) {
			queries = queries.concatWith(Flux.fromStream(Arrays.stream(additionalQueries)));
		}

		record ResultAndSummary(EagerResult result, ResultSummary summary) {
		}

		return queries.flatMapSequential(theQuery -> Mono.just(principal)
				.zipWith(queryEvaluator.getExecutionRequirements(principal, theQuery.text()))
				.flatMap(env -> Mono.fromDirect(this.execute0(env, q -> {
					var rxResult = q.run(query.value());
					return Mono.fromDirect(rxResult.keys())
						.zipWith(Flux.from(rxResult.records()).collectList())
						.map(content -> EagerResult.success(content, query.resultDataContents(), driver.defaultTypeSystem()))
						.flatMap(result -> Mono.just(result).zipWith(Mono.fromDirect(rxResult.consume()), ResultAndSummary::new));
				})))
				.onErrorResume(Neo4jException.class, e -> Mono.just(new ResultAndSummary(EagerResult.error(e), null))))
			.collect(ResultContainer::new, (container, element) -> {
				if (element.result().isError()) {
					container.errors.add(element.result().exception());
				} else {
					container.results.add(element.result());
					container.notifications.addAll(element.summary().notifications());
				}
			});
	}

	@SuppressWarnings("deprecation")
	<T> Publisher<T> execute0(Tuple2<Neo4jPrincipal, QueryEvaluator.ExecutionRequirements> env, Function<RxQueryRunner, Publisher<T>> f) {

		var requirements = env.getT2();
		var sessionSupplier = queryEvaluator.isEnterpriseEdition().
			flatMap(v -> {
				var builder = v ? SessionConfig.builder().withImpersonatedUser(env.getT1().username()) : SessionConfig.builder();
				var sessionConfig = builder
					.withDefaultAccessMode(requirements.target() == QueryEvaluator.Target.WRITERS ? AccessMode.WRITE : AccessMode.READ)
					.build();
				return Mono.fromCallable(() -> driver.rxSession(sessionConfig));
			});

		Flux<T> flow;
		if (requirements.transactionMode() == QueryEvaluator.TransactionMode.IMPLICIT) {
			flow = Flux.usingWhen(sessionSupplier, f, RxSession::close);
		} else {
			flow = switch (requirements.target()) {
				case WRITERS -> Flux.usingWhen(
					sessionSupplier,
					session -> session.writeTransaction(f::apply),
					RxSession::close
				);
				case READERS -> Flux.usingWhen(
					sessionSupplier,
					session -> session.readTransaction(f::apply),
					RxSession::close
				);
			};
		}
		return flow.limitRate(applicationProperties.fetchSize(), applicationProperties.fetchSize() / 2);
	}
}
