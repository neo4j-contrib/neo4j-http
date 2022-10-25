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

import java.util.function.Consumer;

import org.neo4j.driver.Driver;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Supported formats.
 *
 * @author Michael J. Simons
 */
public final class Formats {

	/**
	 * Configures Jackson that way that the "old" request format is supported and all driver values can be serialized.
	 * @param driver The driver
	 * @return The customizer
	 */
	public static Consumer<Jackson2ObjectMapperBuilder> defaultFor(Driver driver) {
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

			builder.serializationInclusion(JsonInclude.Include.NON_EMPTY);
		};
	}

	private Formats() {
	}
}
