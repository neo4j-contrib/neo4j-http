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

import org.neo4j.driver.AuthTokens;
import org.neo4j.http.db.Neo4jPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Processes the authentication from the HTTP request and maps it to the driver's {@link org.neo4j.driver.AuthToken}.
 *
 * Note: Standard Spring practice is for the credentials to be erased from this point but they are kept on purpose
 * so that they can be passed to the driver.
 */
@Component
final class DefaultAuthenticationProvider implements Neo4jAuthenticationProvider {
	@Override
	public Mono<Authentication> authenticate(Authentication authentication) {
		//TODO Support other kinds of AuthToken (e.g bearer, kerberos, none)
		return Mono.<Authentication>fromCallable(() ->
				new UsernamePasswordAuthenticationToken(new Neo4jPrincipal(authentication.getName(),
						AuthTokens.basic(authentication.getName(), (String) authentication.getCredentials())),
						authentication.getCredentials(), List.of())).share();
	}
}
