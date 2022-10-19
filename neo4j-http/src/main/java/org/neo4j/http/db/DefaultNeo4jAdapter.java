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

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

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
class DefaultNeo4jAdapter extends AbstractNeo4jAdapter {

	private final QueryEvaluator queryEvaluator;

	private final Driver driver;

	DefaultNeo4jAdapter(QueryEvaluator queryEvaluator, Driver driver) {
		this.queryEvaluator = queryEvaluator;
		this.driver = driver;
	}

	@Override
	public Flux<Wip> stream(Neo4jPrincipal principal, String query) {
		System.out.println(queryEvaluator.getExecutionRequirements(principal, query));
		return Flux.just(new Wip(List.of()));
	}
}
