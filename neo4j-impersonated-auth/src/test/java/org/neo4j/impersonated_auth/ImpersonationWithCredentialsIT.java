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
package org.neo4j.impersonated_auth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImpersonationWithCredentialsIT {

	static final String DEFAULT_NEO4J_IMAGE = System.getProperty("neo4j-http.default-neo4j-image");

	@SuppressWarnings("resource")
	final Neo4jContainer<?> neo4j = new Neo4jContainer<>(DEFAULT_NEO4J_IMAGE)
		.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
		.withPlugins(MountableFile.forHostPath(Path.of(System.getProperty("neo4j-http.plugins.impersonated-auth.artifact"))))
		.withNeo4jConfig("dbms.security.procedures.unrestricted", "impersonation.authenticate")
		.withReuse(true);

	Driver driver;

	@BeforeAll
	void initNeo4jAndDriver() {
		neo4j.start();
		driver = GraphDatabase.driver(
			neo4j.getBoltUrl(), AuthTokens.basic("neo4j", neo4j.getAdminPassword()),
			Config.builder().withLogging(Logging.none()).build());
		try (var session = driver.session()) {
			session.run("CREATE USER jake IF NOT EXISTS SET PLAINTEXT PASSWORD 'xyz' SET PASSWORD CHANGE NOT REQUIRED").consume();
		}
	}

	@AfterAll
	void closeDriver() {
		driver.close();
	}

	@Test
	public void shouldReturnTrueOnValidUser() {
		try (var session = driver.session()) {
			var result = session.run("RETURN impersonation.authenticate('jake', $1) = 'SUCCESS' AS result", Map.of("1", "xyz".getBytes(StandardCharsets.UTF_8))).single().get(0).asBoolean();
			Assertions.assertTrue(result);
		}
	}

	@Test
	public void shouldReturnFalseOnInvalidUser() {
		try (var session = driver.session()) {
			var result = session.run("RETURN impersonation.authenticate('jake', $1) = 'SUCCESS' AS result", Map.of("1", "foobar".getBytes(StandardCharsets.UTF_8))).single().get(0).asBoolean();
			Assertions.assertFalse(result);
		}
	}
}
