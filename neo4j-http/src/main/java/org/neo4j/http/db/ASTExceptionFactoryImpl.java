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

import org.neo4j.cypher.internal.ast.factory.ASTExceptionFactory;

/**
 * @author Greg Woods
 */
public enum ASTExceptionFactoryImpl implements ASTExceptionFactory {
	INSTANCE;

	@Override
	public Exception syntaxException(String got, List<String> expected, Exception source, int offset, int line, int column) {
		return new RuntimeException(source.getMessage());
	}

	@Override
	public Exception syntaxException(Exception source, int offset, int line, int column) {
		return new RuntimeException(source.getMessage());
	}
}
