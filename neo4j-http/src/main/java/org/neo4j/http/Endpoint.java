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

import org.neo4j.http.db.Neo4jAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The actual HTTP endpoint of this application
 */
@RestController
@RequestMapping("/")
public class Endpoint {

	private final Neo4jAdapter neo4j;

	public Endpoint(Neo4jAdapter neo4j) {
		this.neo4j = neo4j;
	}

	// curl -v  -u neo4j:secret -H "Content-type: application/json" -X POST -d "MATCH (n) RETURN n" localhost:8080/
	@PostMapping
	String wip(Authentication authentication, @RequestBody String query) {
		return neo4j.getQueryTarget(authentication, query).name();
	}

	@GetMapping("/")
	public String index() {
		return "index";
	}
}
