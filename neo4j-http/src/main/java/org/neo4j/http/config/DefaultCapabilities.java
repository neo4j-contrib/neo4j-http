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
package org.neo4j.http.config;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.neo4j.driver.Driver;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.http.db.Capabilities;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties;
import org.springframework.core.env.Environment;

/**
 * A utility class that evaluates whether SSR is available or not. This class is not meant to be a component. If it is used
 * as a component, it should not be used in {@link org.springframework.boot.autoconfigure.condition.ConditionalOnExpression}
 * and friends, as it needs access to a couple of beans in the context which does not work, at least not out of the box
 * and without hassles fighting against the basic {@link  org.springframework.context.annotation.Conditional @Conditional}
 * from Spring Framework or the corresponding autoconfiguration conditions provided by Spring Boot.
 *
 * @author Michael J. Simons
 */
final class DefaultCapabilities implements Capabilities {

	Logger LOGGER = Logger.getLogger(DefaultCapabilities.class.getName());

	private final Environment environment;

	private final Driver driver;

	private final ApplicationProperties properties;

	private final Neo4jProperties neo4jProperties;

	private volatile Boolean ssrAvailable;

	DefaultCapabilities(Environment environment, Driver driver, ApplicationProperties properties, Neo4jProperties neo4jProperties) {
		this.environment = environment;
		this.driver = driver;
		this.neo4jProperties = neo4jProperties;
		this.properties = properties;
	}

	@Override
	public boolean ssrAvailable() {

		Boolean result = this.ssrAvailable;
		if (result == null) {
			synchronized (this) {
				result = this.ssrAvailable;
				if (result == null) {
					this.ssrAvailable = checkSsrAvailability();
					result = this.ssrAvailable;
				}
			}
		}
		return result != null && result;
	}

	private Boolean checkSsrAvailability() {

		if (!"neo4j".equalsIgnoreCase(neo4jProperties.getUri().getScheme())) {
			LOGGER.log(Level.WARNING, () -> String.format("The connection is made via %s and can't use SSR; please use `neo4j://`", neo4jProperties.getUri()));
			return false;
		}

		if (Arrays.stream(environment.getActiveProfiles()).anyMatch("ssr"::equalsIgnoreCase)) {
			LOGGER.log(Level.INFO, "SSR profiles has been explicitly enabled, skipping further evaluation");
			return true;
		}

		try (var session = driver.session()) {
			var cnt = session.run(
				// language=cypher
				"""
					CALL dbms.listConfig() YIELD name, value WITH *
					WHERE name = 'dbms.routing.enabled' AND toBoolean(value) = true
					RETURN COUNT(*);
					"""
			).single().get(0).asLong();
			return cnt > 0;
		} catch (ServiceUnavailableException e) {
			LOGGER.log(Level.WARNING, () -> String.format("Could not determine whether SSR is enabled as no connection could be established, defaulting to %s", properties.defaultToSsr()));
			return properties.defaultToSsr();
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, e, () -> "Could not determine whether SSR is enabled or not");
		}

		return false;
	}
}
