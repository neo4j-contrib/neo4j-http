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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.neo4j.ConfigBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Michael J. Simons
 * @oundtrack Queen - The Miracle
 */
@Configuration(proxyBeanMethods = false)
public class DriverConfig {

	/**
	 * @return changes to the driver
	 */
	@Bean
	ConfigBuilderCustomizer configBuilderCustomizer(@Autowired ApplicationProperties applicationProperties) {
		return builder -> builder
			.withFetchSize(applicationProperties.fetchSize())
			.withUserAgent("neo4j-http-proxy");
	}
}
