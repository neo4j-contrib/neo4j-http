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

import java.nio.file.Path;

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
class ImposterIT {

	// TODO use sys variable for image name like in migrations
	@SuppressWarnings("resource")
	final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:4.4-enterprise")
		.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
		.withPlugins(MountableFile.forHostPath(Path.of(System.getProperty("artifact"))))
		.withNeo4jConfig("dbms.security.procedures.unrestricted", "imposter.authenticate")
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
			var result = session.run("RETURN imposter.authenticate('jake', 'xyz') AS result").single().get(0).asBoolean();
			Assertions.assertTrue(result);
		}
	}

	@Test
	public void shouldReturnFalseOnInvalidUser() {
		try (var session = driver.session()) {
			var result = session.run("RETURN imposter.authenticate('jake', 'foobar') AS result").single().get(0).asBoolean();
			Assertions.assertFalse(result);
		}
	}
}
