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

/**
 * A list of possible views on common Neo4j Java Driver types. Those are mostly indicators what is returned and they are
 * used in the corresponding Jackson modules. Most of the time they won't be instantiated.
 * <p>
 * For usage see (among others) <a href="https://medium.com/@iamitpatil1993/jackson-jsonview-and-its-meaningful-use-with-spring-boot-rest-5fb2ad58dcfe">JsonView and its usage with Spring Boot</a>.
 *
 * @author Michael J.Simons
 */
public final class Views {

	/**
	 * Default view, corresponds with the 4.4 / 5.x state of the HTTP api
	 */
	public interface Default {

	}

	private Views() {
	}
}
