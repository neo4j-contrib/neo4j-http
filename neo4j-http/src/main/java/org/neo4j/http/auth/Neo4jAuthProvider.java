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
import org.neo4j.http.db.Neo4jPrincipal;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Checks a given authentication against the known properties. If this is not successful, it tries to use the configured
 * adapter.
 * <p>
 * A {@link org.springframework.security.core.userdetails.UserDetailsService} is not used here as that would basically delegate to
 * a repository of users and would require someway to retrieve the Neo4j users password (in encoded) form so that it can be compared here with a hash
 * <p>
 * Other authentication providers might be freely chose to do so with the only requirement they populate the
 * {@link java.security.Principal} with a {@link Neo4jPrincipal}.
 *
 * @author Michael J. Simons
 */
@Component
final class Neo4jAuthProvider implements AuthenticationProvider {

	private final Neo4jAdapter neo4j;

	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	private final String serverPassword;
	private final String serverUsername;

	Neo4jAuthProvider(Neo4jProperties neo4jProperties, Neo4jAdapter neo4j) {

		this.neo4j = neo4j;

		this.serverUsername = neo4jProperties.getAuthentication().getUsername();
		this.serverPassword = passwordEncoder.encode(neo4jProperties.getAuthentication().getPassword());
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {

		var name = authentication.getName();
		var password = authentication.getCredentials().toString();

		var principal = new Neo4jPrincipal(name);
		var usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(principal, password, List.of());

		if (serverPassword != null && Objects.equals(serverUsername, name) && passwordEncoder.matches(password, this.serverPassword)) {
			return usernamePasswordAuthenticationToken;
		}

		if (neo4j.canImpersonate(principal, password)) {
			return usernamePasswordAuthenticationToken;
		}

		throw new BadCredentialsException("Could not authenticate" + name);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}
}
