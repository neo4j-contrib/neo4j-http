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
package org.neo4j.http.message;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.http.app.Endpoint;
import org.neo4j.http.config.JacksonConfig;
import org.neo4j.http.db.Neo4jPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

/**
 * Test to ensure that the message conversion works with the given Jackson configuration of the application.
 *
 * @author Gerrit Meier
 */
// We have to exclude the Endpoint because we cannot mock the sealed Neo4jAdapter that is needed there.
@WebFluxTest(excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {Endpoint.class})})
@Import({RequestParsingIT.Config.class, JacksonConfig.class})
public class RequestParsingIT {

	@TestConfiguration
	static class MockConfiguration {

		@MockBean
		Driver driver;
	}

	@Autowired
	WebTestClient client;

	@Test
	void simpleRequestGetsConverted() {
		client.mutateWith(SecurityMockServerConfigurers.csrf())
				.post()
				.uri("/tests/statementrequest")
				.headers((headers) -> {
					headers.setBasicAuth("some", "one");
					headers.setContentType(MediaType.APPLICATION_JSON);
				})
				.bodyValue("""
					{
						"statements": [
							{
								"statement": "MATCH (n) RETURN n",
								"parameters": {"name": "Neo4j-HTTP-Proxy"}
							}
						]
				}""")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.isEqualTo("MATCH (n) RETURN n:{name: \"Neo4j-HTTP-Proxy\"}");
	}

	@Test
	void customTypeRequestGetsConverted() {
		client.mutateWith(SecurityMockServerConfigurers.csrf())
				.post()
				.uri("/tests/statementrequest")
				.headers((headers) -> {
					headers.setBasicAuth("some", "one");
					headers.setContentType(MediaType.APPLICATION_JSON);
				})
				.bodyValue("""
					{
						"statements": [
							{
								"statement": "MATCH (n) RETURN n",
								"parameters": {"someDate": {"$type": "Date", "_value": "2022-10-21"}}
							}
						]
				}""")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.isEqualTo("MATCH (n) RETURN n:{someDate: 2022-10-21}");
	}

	@Test
	void invalidRequestTypeFormat() {
		client.mutateWith(SecurityMockServerConfigurers.csrf())
				.post()
				.uri("/tests/statementrequest")
				.headers((headers) -> {
					headers.setBasicAuth("some", "one");
					headers.setContentType(MediaType.APPLICATION_JSON);
				})
				.bodyValue("""
					{
						"statements": [
							{
								"statement": "MATCH (n) RETURN n",
								"parameters": {"someDate": {"$type": "Date", "_value": "2022-21-10"}}
							}
						]
				}""")
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void invalidRequestTypeValue() {
		client.mutateWith(SecurityMockServerConfigurers.csrf())
				.post()
				.uri("/tests/statementrequest")
				.headers((headers) -> {
					headers.setBasicAuth("some", "one");
					headers.setContentType(MediaType.APPLICATION_JSON);
				})
				.bodyValue("""
					{
						"statements": [
							{
								"statement": "MATCH (n) RETURN n",
								"parameters": {"someDate": {"$type": "Date", "_value": true}}
							}
						]
				}""")
				.exchange()
				.expectStatus().isBadRequest();
	}

	static class Config {

		@Bean
		ReactiveAuthenticationManager neo4jAuthenticationProvider() {
			return authentication ->
					Mono.just(new UsernamePasswordAuthenticationToken(
							new Neo4jPrincipal("some", "password"), authentication.getCredentials(), List.of())
					);
		}

	}

}
