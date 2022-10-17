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
package org.neo4j.http.db;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

import org.neo4j.driver.Driver;
import org.neo4j.driver.exceptions.ClientException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Notes:
 * <ul>
 *     <li>Only credentials supported are {@link String}, mapping to an unencoded password</li>
 * </ul>
 *
 * @author Michael J. Simons
 */
@Service
@Primary
class BoltAdapterImpl extends AbstractNeo4jAdapter {

	private final Driver driver;

	BoltAdapterImpl(Driver driver) {
		this.driver = driver;
	}

	@Cacheable("queryTargets")
	@Override
	public Target getQueryTarget(Neo4jPrincipal principal, String query) {
		return Target.UNDECIDED;
	}

	@Override
	public boolean canImpersonate(Neo4jPrincipal principal, Object credentials) {

		var name = principal.username();
		var password = credentials.toString();

		try (var session = driver.session()) {
			return session.run("RETURN impersonation.authenticate($0, $1) = 'SUCCESS' AS result", Map.of(
				"0", name,
				"1", password.getBytes(StandardCharsets.UTF_8))
			).single().get(0).asBoolean();
		} catch (ClientException e) {
			if (!"Neo.ClientError.Statement.SyntaxError".equals(e.code())) {
				LOGGER.log(Level.SEVERE, "Error checking authentication prior to impersonation", e);
			} else {
				LOGGER.log(Level.WARNING, "impersonated-auth plugin is not installed, cannot authenticate user");
			}
			return false;
		}
	}
}
