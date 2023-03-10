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
import org.neo4j.http.message.DefaultRequestFormatModule;
import org.neo4j.http.message.DefaultResponseModule;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Configures Jackson.
 *
 * @author Michael J. Simons
 */
@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding({
	DefaultRequestFormatModule.AnnotatedQueryContainerMixIn.class,
	DefaultResponseModule.InputPositionMixIn.class,
	DefaultResponseModule.Neo4jExceptionMixIn.class,
	DefaultResponseModule.NotificationMixIn.class,
})
public class JacksonConfig {

	/**
	 * @param driver needed to retrieve the type-system
	 * @return changes to the default, application context wide instance of {@link com.fasterxml.jackson.databind.ObjectMapper}
	 */
	@Bean
	public Jackson2ObjectMapperBuilderCustomizer objectMapperBuilderCustomizer(@Autowired Driver driver) {

		return builder -> {
			builder.modules(
				new DefaultRequestFormatModule(),
				new DefaultResponseModule(driver.defaultTypeSystem()),
				new JavaTimeModule()
			);

			builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
			builder.featuresToDisable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

			builder.featuresToEnable(MapperFeature.DEFAULT_VIEW_INCLUSION);
			builder.featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

			builder.serializationInclusion(JsonInclude.Include.NON_ABSENT);
		};
	}
}
