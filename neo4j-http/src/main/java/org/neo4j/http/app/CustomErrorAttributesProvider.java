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
package org.neo4j.http.app;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.http.db.InvalidQueryException;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * Sets error attributes based on the Neo4j exception. I personally would find a {@link org.springframework.web.server.WebExceptionHandler}
 * or maybe even a {@link org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler} more sensible to just
 * specify the attributes and status code, but that would require recreating the rendering core from
 * {@link org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler} as well. The system
 * feels much inferior to Spring WebMVC, as a matter of fact, even the documentation under
 * <a href="https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-exception-handler">Exceptions</a>
 * isn't that helpful at all.
 *
 * @author Michael J. Simons
 */
@Component
final class CustomErrorAttributesProvider extends DefaultErrorAttributes {

	@Override
	public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {

		var errorAttributes = super.getErrorAttributes(request, options);
		var error = getError(request);
		if (error instanceof InvalidQueryException invalidQueryException) {
			var s = new HashMap<>(errorAttributes);
			s.put("error", "Invalid query");
			s.put("message", invalidQueryException.getQuery());
			s.put("status", HttpStatus.BAD_REQUEST.value());
			s.remove("trace");
			return s;
		}

		return errorAttributes;
	}
}
