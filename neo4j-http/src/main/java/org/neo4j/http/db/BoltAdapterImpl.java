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

import org.neo4j.driver.Driver;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
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
	public Target getQueryTarget(Authentication authentication, String query) {
		return Target.UNDECIDED;
	}

	@Override
	public boolean canImpersonate(Authentication authentication) {

		var name = authentication.getName();
		var password = authentication.getCredentials().toString();

		try (var session = driver.session()) {
			return session.run("RETURN impersonation.authenticate($0, $1) = 'SUCCESS' AS result", Map.of(
				"0", name,
				"1", password.getBytes(StandardCharsets.UTF_8))
			).single().get(0).asBoolean();
		}
	}
}
