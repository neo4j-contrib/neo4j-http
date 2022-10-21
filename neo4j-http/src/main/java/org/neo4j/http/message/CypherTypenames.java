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

enum CypherTypenames {

	NULL,

	List,

	Map,

	Boolean,

	Integer,

	Float,

	String,

	ByteArray("Byte[]"),

	Date,

	Time,

	LocalTime,

	DateTime,

	LocalDateTime,

	Duration,

	Period,

	Point,

	Node,

	Relationship,

	Path;


	private final String value;

	CypherTypenames() {
		this(null);
	}

	CypherTypenames(String value) {
		this.value = value == null ? this.name() : value;
	}

	public String getValue() {
		return value;
	}
}
