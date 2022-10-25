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
package org.neo4j.http.app;

import org.neo4j.driver.Record;
import org.neo4j.http.db.AnnotatedQuery;
import org.neo4j.http.db.Neo4jAdapter;
import org.neo4j.http.db.Neo4jPrincipal;
import org.neo4j.http.db.ResultContainer;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The actual HTTP endpoint of this application
 */
@RestController
@RequestMapping("/")
public class Endpoint {

	private final Neo4jAdapter neo4j;

	/**
	 * @param neo4j all access to Neo4j goes through this adapter.
	 */
	public Endpoint(Neo4jAdapter neo4j) {
		this.neo4j = neo4j;
	}

	@JsonView(Views.NEO4J_44_DEFAULT.class)
	@PostMapping(value = "/b", produces = MediaType.APPLICATION_JSON_VALUE)
	Mono<ResultContainer> wip1(@AuthenticationPrincipal Neo4jPrincipal authentication, @RequestBody AnnotatedQuery.Container queries) {

		if (queries.value().isEmpty()) {
			throw new IllegalArgumentException("No query given");
		}
		return neo4j.run(authentication, queries.value().get(0), queries.value().stream().skip(1).toArray(AnnotatedQuery[]::new));
	}

	@PostMapping(value = "/b", produces = MediaType.APPLICATION_NDJSON_VALUE)
	Flux<Record> wip2(@AuthenticationPrincipal Neo4jPrincipal authentication, @RequestBody String query) {
		return neo4j.stream(authentication, query);
	}
}
