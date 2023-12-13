package org.neo4j.http.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Experimental Cors config.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig implements WebFluxConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/db/**")
			.allowedOrigins("http://localhost:3000")
			.allowedMethods("GET", "PUT", "POST", "DELETE")
			.allowedHeaders("*")
			.allowCredentials(true).maxAge(3600);
	}
}
