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

import org.neo4j.driver.Query;
import org.neo4j.http.db.Neo4jAdapter;
import org.neo4j.http.db.Neo4jPrincipal;
import org.neo4j.http.grpc.Neo4JService;
import org.neo4j.http.grpc.Neo4jServiceGrpc;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * @author Michael J. Simons
 */
@GrpcService
public class GrpcEndpoint extends Neo4jServiceGrpc.Neo4jServiceImplBase {

	private final Neo4jAdapter neo4j;

	public GrpcEndpoint(Neo4jAdapter neo4j) {
		this.neo4j = neo4j;
	}

	@Override
	public void stream(Neo4JService.Query request, StreamObserver<Neo4JService.Record> responseObserver) {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		neo4j.stream((Neo4jPrincipal) authentication.getPrincipal(), "neo4j", new Query(request.getText()))
			.map(r -> {
				var rb = Neo4JService.Record.newBuilder();
				// This is shit, it won't work with null values and nodes look stupid too, but alas…
				rb.addAllValues(r.values(v -> Neo4JService.Value.newBuilder().setContent(v.asObject().toString()).build()));
				return rb.build();
			})
			.subscribe(responseObserver::onNext, responseObserver::onError, responseObserver::onCompleted);
	}
}
