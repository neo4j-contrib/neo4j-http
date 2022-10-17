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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.http.db.Neo4jPrincipal;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties;
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
final class BoltAuthenticationProvider implements Neo4jAuthenticationProvider {

	private final Driver boltConnection;
	private final SessionConfig read_session_config;

	private final PasswordEncoder passwordEncoder;

	private final String serverPassword;
	private final String serverUsername;

	BoltAuthenticationProvider(Neo4jProperties neo4jProperties, Driver boltConnection) {

		this.boltConnection = boltConnection;
		this.read_session_config = SessionConfig.builder().withDefaultAccessMode(AccessMode.READ).build();

		this.passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

		this.serverUsername = neo4jProperties.getAuthentication().getUsername();
		this.serverPassword = passwordEncoder.encode(Objects.requireNonNull(neo4jProperties.getAuthentication().getPassword()));
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {

		var name = authentication.getName();
		var password = authentication.getCredentials().toString();

		if (Objects.equals(serverUsername, name) && passwordEncoder.matches(password, this.serverPassword) || canImpersonate(name, password)) {
			var principal = new Neo4jPrincipal(name);
			return new UsernamePasswordAuthenticationToken(principal, password, List.of());
		}

		throw new BadCredentialsException("Could not authenticate" + name);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return authentication.equals(UsernamePasswordAuthenticationToken.class);
	}

	/**
	 * A helper method for drivers that don't allow to use any authentication for impersonation. The main goal is actually
	 * not to have that method at all, right now, it is all that is possible for having only one driver instance and make
	 * use of multi users / impersonation.
	 *
	 * @param username   The username to check
	 * @param password The password to try to authenticate with
	 * @return {@literal true} if the given {@link Authentication} can be safely used as impersonated user
	 */
	boolean canImpersonate(String username, String password) {

		try (var session = boltConnection.session(read_session_config)) {
			return session.run("RETURN impersonation.authenticate($0, $1) = 'SUCCESS' AS result", Map.of(
				"0", username,
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
