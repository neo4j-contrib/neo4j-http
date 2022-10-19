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

import java.util.logging.Logger;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ReactiveAuthenticationManager;

/**
 * Facade over Spring's own {@link ReactiveAuthenticationManager}.
 *
 * @author Michael J. Simons
 */
public sealed interface Neo4jAuthenticationProvider extends ReactiveAuthenticationManager permits BoltAuthenticationProvider {

	/**
	 * Shared logger for all authentication provider instances.
	 */
	Logger LOGGER = Logger.getLogger(Neo4jAuthenticationProvider.class.getName());
}
