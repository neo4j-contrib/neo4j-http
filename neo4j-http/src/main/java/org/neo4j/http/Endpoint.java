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

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.neo4j.driver.Driver;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.http.db.Neo4jAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	@PostMapping
	String wip(@AuthenticationPrincipal Object authentication, @RequestBody String query) {

		return neo4j.getQueryTarget(null, query).name() + ": " + authentication;
	}


	@Autowired
	ObjectMapper objectMapper;

	@GetMapping("/a")
	public StreamingResponseBody things() {
		System.out.println(">> " + Thread.currentThread().getId() + " " + Thread.currentThread().getName());
// return CompletableFuture.completedFuture("x");
		return new StreamingResponseBody() {

			@Override
			public void writeTo(OutputStream outputStream) throws IOException {
				System.out.println("writing tx" + Thread.currentThread().getId() + " " + Thread.currentThread().getName());
				JsonGenerator jc = objectMapper.createGenerator(outputStream);
				jc.writeStartObject();
				jc.writeEndObject();
				jc.flush();
			}
		};
		/*
		AsyncSession session = driver.asyncSession();
		CompletionStage<List<String>> cs = session
			.readTransactionAsync(tx -> tx // <- the very first attempt is likely to execute using whatever thread that calls this, but retries will be called by Netty thread
				.runAsync("MATCH (f:Movie) RETURN f ORDER BY f.title")
				.thenCompose(cursor -> { // <- you could use thenComposeAsync with your own executor to switch if you wish to
					System.out.println(Thread.currentThread().getId() + "; Thread "+ Thread.currentThread().getName());
					return cursor
						.listAsync(record -> record.get("f").get("title").asString());
				}))
		return cs.thenCompose(fruits -> session.closeAsync().thenApplyAsync(signal -> fruits));*/
	}

	@GetMapping("/")
	public String index() {
		return "index";
	}
}
