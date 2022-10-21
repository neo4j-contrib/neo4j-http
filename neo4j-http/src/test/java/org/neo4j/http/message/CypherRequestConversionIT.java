package org.neo4j.http.message;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.http.app.Endpoint;
import org.neo4j.http.db.Neo4jPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

// We have to exclude the Endpoint because we cannot mock the sealed Neo4jAdapter that is needed there.
@WebFluxTest(excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {Endpoint.class})})
@Import(CypherRequestConversionIT.Config.class)
public class CypherRequestConversionIT {

	@MockBean
	Driver driver;

	@Autowired
	WebTestClient client;


	@Test
	void simpleRequestGetsConverted() {
		client.mutateWith(csrf()).post()
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
				.isEqualTo("MATCH (n) RETURN n:{name=Neo4j-HTTP-Proxy}");
	}

	@Test
	void customTypeRequestGetsConverted() {
		client.mutateWith(csrf()).post()
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
								"parameters": {"someDate": {"type": "LocalDate", "value": "2022-10-21"}}
							}
						]
				}""")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.isEqualTo("MATCH (n) RETURN n:{someDate={type=LocalDate, value=2022-10-21}}");
	}

	static class Config {

		@Bean
		ReactiveAuthenticationManager neo4jAuthenticationProvider() {
			return authentication ->
					Mono.just(new UsernamePasswordAuthenticationToken(
							new Neo4jPrincipal("some"), authentication.getCredentials(), List.of())
					);
		}

	}

}
