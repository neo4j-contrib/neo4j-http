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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.summary.Notification;

/**
 * Instances of this class are treated as mutable internal to this package.
 *
 * @author Michael J. Simons
 * @soundtrack Nightwish - Decades: Live In Buenos Aires
 */
public final class ResultContainer {

	final List<EagerResult> results;
	final List<Notification> notifications;
	final List<Neo4jException> errors;

	public ResultContainer() {
		this.results = new ArrayList<>();
		this.notifications = new ArrayList<>();
		this.errors = new ArrayList<>();
	}

	/**
	 * {@return an unmodifiable list of results}
	 */
	public List<EagerResult> getResults() {
		return Collections.unmodifiableList(results);
	}

	/**
	 * {@return an unmodifiable list of notifications}
	 */
	public List<Notification> getNotifications() {
		return Collections.unmodifiableList(notifications);
	}

	/**
	 * {@return an unmodifiable list of Neo4j errors}
	 */
	public List<Neo4jException> getErrors() {
		return Collections.unmodifiableList(errors);
	}
}
