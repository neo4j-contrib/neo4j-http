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
package org.neo4j.http;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.http.db.Neo4jAdapter;
import org.neo4j.http.db.Neo4jPrincipal;
import org.neo4j.http.db.Wip;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

/**
 * The actual HTTP endpoint of this application
 */
@RestController
@RequestMapping("/")
public class Endpoint {

	private final Neo4jAdapter neo4j;


	@Autowired
	Driver driver;

	public Endpoint(Neo4jAdapter neo4j) {
		this.neo4j = neo4j;
	}

	// curl -v  -u neo4j:secret -H "Content-type: application/json" -X POST -d "MATCH (n) RETURN n" localhost:8080/
	/*
	@PostMapping
	String wip(@AuthenticationPrincipal Neo4jPrincipal authentication, @RequestBody String query) {
		return neo4j.getQueryTarget(authentication, query).name() + ": " + authentication;
	}
*/
	@PostMapping(value = "/b", produces = MediaType.APPLICATION_NDJSON_VALUE)
	Flux<Wip> wip2(@AuthenticationPrincipal Neo4jPrincipal authentication, @RequestBody String query) {
		return neo4j.stream(authentication, query);
	}

	@Autowired
	ObjectMapper objectMapper;

	@GetMapping(value = "/a", produces = MediaType.APPLICATION_NDJSON_VALUE)
	public Flux<Wip> things() {
		return Flux.range(0, 230)
			.delayElements(Duration.ofMillis(500))
			.map(i -> new Wip(List.of("foo", Map.of("a", "b"))));
	}

	@Autowired
	TaskExecutor executor;

	@GetMapping("/")
	public String index() {
		return "index";
	}
}
