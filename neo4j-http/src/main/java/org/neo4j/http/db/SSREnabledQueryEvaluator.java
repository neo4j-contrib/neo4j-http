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

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import org.neo4j.driver.Driver;

/**
 * Simple Query evaluator that only determines {@link TransactionMode} when Server-Side routing has been enabled.
 *
 * @author Greg Woods
 */
@Service
@Profile("ssr")
public class SSREnabledQueryEvaluator implements QueryEvaluator {
	private final Mono<Boolean> enterpriseEdition;

	public SSREnabledQueryEvaluator(Driver driver) {
		this.enterpriseEdition = QueryEvaluator.isEnterprise(driver);
	}

	@Override
	public Mono<Boolean> isEnterpriseEdition() {
		return enterpriseEdition;
	}

	@Cacheable("executionRequirements")
	@Override
	public Mono<ExecutionRequirements> getExecutionRequirements(Neo4jPrincipal principal, String query) {
		return Mono.just(Target.WRITERS).zipWith(QueryEvaluator.getTransactionMode(query), ExecutionRequirements::new).cache();
	}

}
