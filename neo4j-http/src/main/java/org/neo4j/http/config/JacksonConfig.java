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

import org.neo4j.driver.Driver;
import org.neo4j.http.message.DriverTypeSystemModule;
import org.neo4j.http.message.ParameterTypesModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Jackson.
 *
 * @author Michael J. Simons
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

	/**
	 * @param driver needed to retrieve the typesystem
	 * @return changes to the default, application context wide instance of {@link com.fasterxml.jackson.databind.ObjectMapper}
	 */
	@Bean
	public Jackson2ObjectMapperBuilderCustomizer objectMapperBuilderCustomizer(@Autowired Driver driver) {
		return builder -> {
			// Whatever is necessary, added as placeholder
			builder.modules(new ParameterTypesModule());
			builder.modules(new DriverTypeSystemModule(driver.defaultTypeSystem()));
		};
	}
}
