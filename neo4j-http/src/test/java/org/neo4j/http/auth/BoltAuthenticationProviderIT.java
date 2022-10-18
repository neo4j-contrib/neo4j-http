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
package org.neo4j.http.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Tests around authentication
 *
 * @author Michael J. Simons
 */
@Nested
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class BoltAuthenticationProviderIT {

	static final String DEFAULT_NEO4J_IMAGE = System.getProperty("neo4j-http.default-neo4j-image");

	@SuppressWarnings("resource")
	private static final Neo4jContainer<?> neo4j = new Neo4jContainer<>(DEFAULT_NEO4J_IMAGE)
		.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
		.withPlugins(MountableFile.forHostPath(Path.of(System.getProperty("neo4j-http.plugins.impersonated-auth.artifact"))))
		.withNeo4jConfig("dbms.security.procedures.unrestricted", "impersonation.authenticate")
		.withReuse(true);

	@DynamicPropertySource
	static void prepareNeo4j(DynamicPropertyRegistry registry) {

		neo4j.start();
		try (
			var driver = GraphDatabase.driver(neo4j.getBoltUrl(), AuthTokens.basic("neo4j", neo4j.getAdminPassword()), Config.builder().withLogging(Logging.none()).build());
			var session = driver.session()
		) {
			session.run("CREATE USER jake IF NOT EXISTS SET PLAINTEXT PASSWORD 'xyz' SET PASSWORD CHANGE NOT REQUIRED").consume();
		}

		registry.add("spring.neo4j.authentication.username", () -> "neo4j");
		registry.add("spring.neo4j.authentication.password", neo4j::getAdminPassword);
		registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
	}

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void shouldUseDriverConnection() {

		var exchange = this.restTemplate
			.withBasicAuth("neo4j", neo4j.getAdminPassword())
			.exchange("/tests/", HttpMethod.GET, null, String.class);
		assertThat(exchange.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(exchange.getBody()).isEqualTo("index");
	}

	@Test
	void shouldCheckImposter() {

		var exchange = this.restTemplate
			.withBasicAuth("jake", "xyz")
			.exchange("/tests/", HttpMethod.GET, null, String.class);
		assertThat(exchange.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(exchange.getBody()).isEqualTo("index");
	}

	@Test
	void shouldCheckImposterAndFailIfUnknown() {

		var exchange = this.restTemplate
			.withBasicAuth("jake", "blah")
			.exchange("/tests/", HttpMethod.GET, null, String.class);
		assertThat(exchange.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
