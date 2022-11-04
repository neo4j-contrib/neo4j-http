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
import org.neo4j.driver.Query;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.reactivestreams.ReactiveResult;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.http.db.Neo4jPrincipal;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
final class DefaultAuthenticationProvider implements Neo4jAuthenticationProvider {

	private static final SessionConfig DEFAULT_SESSION_CONFIG = SessionConfig.builder().withDefaultAccessMode(AccessMode.READ).build();

	private final Driver boltConnection;

	private final PasswordEncoder passwordEncoder;

	private final String serverPassword;
	private final String serverUsername;

	DefaultAuthenticationProvider(Neo4jProperties neo4jProperties, Driver boltConnection) {

		this.boltConnection = boltConnection;

		this.passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

		this.serverUsername = neo4jProperties.getAuthentication().getUsername();
		this.serverPassword = passwordEncoder.encode(Objects.requireNonNull(neo4jProperties.getAuthentication().getPassword()));
	}

	@Override
	public Mono<Authentication> authenticate(Authentication authentication) {

		var authenticatedPrincipal = Mono.<Authentication>fromCallable(() -> {
			var name = authentication.getName();
			return new UsernamePasswordAuthenticationToken(new Neo4jPrincipal(name), authentication.getCredentials(), List.of());
		}).share();

		return authenticatedPrincipal
			.filter(p -> Objects.equals(p.getName(), serverUsername) && passwordEncoder.matches((String) p.getCredentials(), this.serverPassword))
			.switchIfEmpty(authenticatedPrincipal.filterWhen(this::canImpersonate))
			.switchIfEmpty(Mono.error(new BadCredentialsException("Could not authenticate" + authentication.getName())));
	}

	/**
	 * A helper method for drivers that don't allow to use any authentication for impersonation. The main goal is actually
	 * not to have that method at all, right now, it is all that is possible for having only one driver instance and make
	 * use of multi users / impersonation.
	 *
	 * @param principalAndPassword The principal and password to check
	 * @return {@literal true} if the given {@link Authentication} can be safely used as impersonated user
	 */
	@SuppressWarnings("deprecation")
	Mono<Boolean> canImpersonate(Authentication principalAndPassword) {

		var query = new Query(
			"RETURN impersonation.authenticate($0, $1) = 'SUCCESS' AS result",
			Map.of(
				"0", principalAndPassword.getName(),
				"1", ((String) principalAndPassword.getCredentials()).getBytes(StandardCharsets.UTF_8)
			)
		);

		return Mono.usingWhen(
			Mono.fromCallable(() -> boltConnection.session(ReactiveSession.class, DEFAULT_SESSION_CONFIG)),
			session -> Flux.from(session.run(query)).flatMapSequential(ReactiveResult::records)
				.map(record -> record.get(0).asBoolean())
				.single(),
			ReactiveSession::close
		).onErrorResume(ClientException.class, e -> {
			if (!"Neo.ClientError.Statement.SyntaxError".equals(e.code())) {
				LOGGER.log(Level.SEVERE, "Error checking authentication prior to impersonation", e);
			} else {
				LOGGER.log(Level.WARNING, "impersonated-auth plugin is not installed, cannot authenticate user");
			}
			return Mono.just(Boolean.FALSE);
		});
	}
}
