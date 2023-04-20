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

import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.http.db.Neo4jPrincipal;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 *
 */
@Component
final class DefaultAuthenticationProvider implements Neo4jAuthenticationProvider {

	//TODO must be more suitable approach
	@Override
	public Mono<Authentication> authenticate(Authentication authentication) {
		return Mono.<Authentication>fromCallable(() ->
				new UsernamePasswordAuthenticationToken(new Neo4jPrincipal(authentication.getName(), (String) authentication.getCredentials()), authentication.getCredentials(), List.of())).share();
	}
}
