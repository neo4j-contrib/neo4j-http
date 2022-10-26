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

import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Michael J. Simons
 * @param fetchSize The fetch size is important to create proper throughput
 * @param verifyConnectivity Set to {@literal true} to enable verification of the connection during startup
 * @soundtrack Queen - The Miracle
 */
@ConfigurationProperties("org.neo4j.http")
public record ApplicationProperties(
	Integer fetchSize,
	boolean verifyConnectivity
) {

	/**
	 * @param fetchSize defaults to 2000 if not set
	 * @param verifyConnectivity Set to {@literal true} to enable verification of the connection during startup
	 */
	public ApplicationProperties {
		fetchSize = Optional.ofNullable(fetchSize).orElse(2000);
	}
}
