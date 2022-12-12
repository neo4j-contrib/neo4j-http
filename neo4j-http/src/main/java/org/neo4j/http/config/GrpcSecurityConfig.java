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

import java.util.ArrayList;
import java.util.List;

import org.neo4j.http.auth.Neo4jAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.UnanimousBased;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import net.devh.boot.grpc.server.security.authentication.BasicGrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.CompositeGrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.check.AccessPredicate;
import net.devh.boot.grpc.server.security.check.AccessPredicateVoter;
import net.devh.boot.grpc.server.security.check.GrpcSecurityMetadataSource;
import net.devh.boot.grpc.server.security.check.ManualGrpcSecurityMetadataSource;

/**
 * @author Michael J. Simons
 */
@Configuration(proxyBeanMethods = false)
public class GrpcSecurityConfig {

	@Bean
	AuthenticationManager authenticationManager(Neo4jAuthenticationProvider neo4jAuthenticationProvider) {
		final List<AuthenticationProvider> providers = new ArrayList<>();
		providers.add(new AuthenticationProvider() {

			@Override
			public Authentication authenticate(Authentication authentication) throws AuthenticationException {

				try {
					return neo4jAuthenticationProvider.authenticate(authentication)
						.block();
				} catch (RuntimeException e) {
					if (e.getCause() instanceof BadCredentialsException badCredentialsException) {
						throw badCredentialsException;
					}
					throw e;
				}
			}

			@Override
			public boolean supports(Class<?> authentication) {
				return UsernamePasswordAuthenticationToken.class.equals(authentication);
			}
		});
		return new ProviderManager(providers);
	}

	@Bean
	GrpcAuthenticationReader authenticationReader() {
		return new CompositeGrpcAuthenticationReader(List.of(
			new BasicGrpcAuthenticationReader(),
			(serverCall, metadata) -> {
				throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
			}
		));
	}

	@Bean
	GrpcSecurityMetadataSource grpcSecurityMetadataSource() {
		final ManualGrpcSecurityMetadataSource source = new ManualGrpcSecurityMetadataSource();
		source.setDefault(AccessPredicate.authenticated());
		return source;
	}

	@Bean
	AccessDecisionManager accessDecisionManager() {
		final List<AccessDecisionVoter<?>> voters = new ArrayList<>();
		voters.add(new AccessPredicateVoter());
		return new UnanimousBased(voters);
	}
}
