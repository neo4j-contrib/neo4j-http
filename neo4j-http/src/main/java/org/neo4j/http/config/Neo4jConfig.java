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

import java.util.logging.Logger;

import org.neo4j.driver.BookmarkManager;
import org.neo4j.driver.BookmarkManagerConfig;
import org.neo4j.driver.BookmarkManagers;
import org.neo4j.driver.Driver;
import org.neo4j.driver.MetricsAdapter;
import org.neo4j.http.db.Capabilities;
import org.neo4j.http.db.QueryEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.neo4j.ConfigBuilderCustomizer;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configures all beans necessary to interact with a Neo4j database. The database can be a single instance or a cluster
 * instance. All versions supported by the driver in use are fine.
 *
 * @author Michael J. Simons
 * @soundtrack Queen - The Miracle
 */
@Configuration(proxyBeanMethods = false)
public class Neo4jConfig {

	Logger LOGGER = Logger.getLogger(Neo4jConfig.class.getName());

	/**
	 * Makes sure that some drivers properties can't be overwritten by the external configuration.
	 *
	 * @return changes to the driver
	 */
	@Bean
	ConfigBuilderCustomizer configBuilderCustomizer(@Autowired ApplicationProperties applicationProperties) {
		return builder -> builder
			.withFetchSize(applicationProperties.fetchSize())
			.withMetricsAdapter(MetricsAdapter.MICROMETER)
			.withDriverMetrics()
			.withUserAgent("neo4j-http-proxy");
	}

	/**
	 * @return a bookmark manager as provided by the driver, delegating all causal chaining from us towards the driver.
	 */
	@Bean
	BookmarkManager bookmarkManager() {
		return BookmarkManagers.defaultManager(BookmarkManagerConfig.builder().build());
	}

	/**
	 * The application runner will optionally verify the connection on startup and fail if the configured instance can't be reached.
	 *
	 * @param applicationProperties Properties belonging to this application
	 * @param neo4jProperties       Properties belonging to the drivers autoconfiguration
	 * @param driver                The driver as configured by Spring Boot and our customizer
	 * @return an application runner
	 */
	@Bean
	ApplicationRunner applicationRunner(
		@Autowired ApplicationProperties applicationProperties,
		@Autowired Neo4jProperties neo4jProperties,
		@Autowired Driver driver
	) {
		return args -> {
			if (!applicationProperties.verifyConnectivity()) {
				LOGGER.info("Not verifying connectivity on startup");
				return;
			}
			try {
				driver.verifyConnectivity();
			} catch (Exception e) {
				throw new RuntimeException("Could not verify connection towards " + neo4jProperties.getUri() + ": " + e.getLocalizedMessage());
			}
		};
	}

	/**
	 * Retrieves capabilities of the target Neo4j instance from both the environment and optionally, the driver.
	 *
	 * @param environment The current spring environment
	 * @param driver      The driver as configured by Spring Boot and our customizer
	 * @param applicationProperties Properties belonging to this application
	 * @param neo4jProperties       Properties belonging to the drivers autoconfiguration
	 * @return the capabilities of the target Neo4j instance
	 */
	@Bean
	Capabilities capabilities(Environment environment, Driver driver, ApplicationProperties applicationProperties, Neo4jProperties neo4jProperties) {
		return new DefaultCapabilities(environment, driver, applicationProperties, neo4jProperties);
	}

	/**
	 * The reason for this configuration method and the indirection towards a factory method is rather simple: None of the
	 * {@link org.springframework.context.annotation.Conditional} allow for easy access on already existing beans. While
	 * access to {@link org.springframework.beans.factory.config.BeanDefinition bean definitions} is possible, this is not
	 * enough to check on something via the driver. While there's certainly some way to make this work, a functional approach
	 * like the one below is most likely more maintainable in the future than any hacking into Spring's internals or recreating
	 * for example a Spring Data repository factor approach.
	 *
	 * @return A query evaluator based on the given capabilities.
	 */
	@Bean
	QueryEvaluator queryEvaluator(Driver driver, Capabilities capabilities) {
		return QueryEvaluator.create(driver, capabilities);
	}
}
