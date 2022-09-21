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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Tests around authentication
 *
 * @author Michael J. Simons
 */
class SimpleNeo4jAuthProviderIT {

	@Nested
	@SpringBootTest
	class WithoutConnection {

		@DynamicPropertySource
		static void neo4jProperties(DynamicPropertyRegistry registry) {
			registry.add("spring.neo4j.authentication.username", () -> "Danger");
			registry.add("spring.neo4j.authentication.password", () -> "Dan");
		}

		private static MockMvc mockMvc;

		@BeforeAll
		static void setupMockMvc(@Autowired WebApplicationContext applicationContext) {
			mockMvc = MockMvcBuilders
				.webAppContextSetup(applicationContext)
				.apply(springSecurity())
				.build();
		}

		@Test
		void shouldUsePropertiesWithoutResolvingAConnection() throws Exception {

			var body = mockMvc.perform(get("/tests/").with(
					user("Danger").password("Dan")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

			assertThat(body).isEqualTo("index");
		}

		@Test
		void mustRequireAuth() throws Exception {

			mockMvc.perform(get("/tests/"))
				.andExpect(status().isUnauthorized());
		}
	}
}
