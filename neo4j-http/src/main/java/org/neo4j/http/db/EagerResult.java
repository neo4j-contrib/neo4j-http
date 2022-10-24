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
package org.neo4j.http.db;

import java.util.List;

import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.Neo4jException;

import reactor.util.function.Tuple2;

/**
 * A view on a list of records, the columns contained and potentially errors or messages. Instances of this record are
 * potentially mutable as we want to avoid creating copies of potentially large lists during construction. Please refrain
 * from changing the contents.
 *
 * @author Michael J. Simons
 * @param columns   The columns of the result set
 * @param data      The actual data
 * @param exception An optional exception. If it is {@literal null}, both {@link #columns} and {@link #data} will not be empty
 * @soundtrack Nightwish - Decades: Live In Buenos Aires
 */
public record EagerResult(List<String> columns, List<Record> data, Neo4jException exception) {

	static EagerResult success(Tuple2<List<String>, List<Record>> content) {
		return new EagerResult(content.getT1(), content.getT2(), null);
	}

	static EagerResult error(Neo4jException exception) {
		return new EagerResult(List.of(), List.of(), exception);
	}

	boolean isError() {
		return exception != null;
	}
}
