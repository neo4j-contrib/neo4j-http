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

import java.util.List;
import java.util.Objects;

import org.neo4j.http.db.Neo4jAdapter;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Checks a given authentication against the known properties. If this is not successful, it tries to use the configured
 * adapter.
 *
 * @author Michael J. Simons
 */
@Component
public final class Neo4jAuthProvider implements AuthenticationProvider {

	private final Neo4jProperties neo4jProperties;

	private final Neo4jAdapter neo4j;

	Neo4jAuthProvider(Neo4jProperties neo4jProperties, Neo4jAdapter neo4j) {

		this.neo4jProperties = neo4jProperties;
		this.neo4j = neo4j;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {

		var name = authentication.getName();
		var password = authentication.getCredentials().toString();

		var serverUsername = neo4jProperties.getAuthentication().getUsername();
		var serverPassword = neo4jProperties.getAuthentication().getPassword();

		if (serverPassword != null && Objects.equals(serverUsername, name) && Objects.equals(serverPassword, password)) {
			return new UsernamePasswordAuthenticationToken(name, password, List.of());
		}

		if (neo4j.canImpersonate(authentication)) {
			return new UsernamePasswordAuthenticationToken(name, password, List.of());
		}

		throw new BadCredentialsException("Could not authenticate" + name);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}
}
