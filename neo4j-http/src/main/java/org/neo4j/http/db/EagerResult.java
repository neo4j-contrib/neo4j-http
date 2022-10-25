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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.TypeSystem;

import reactor.util.function.Tuple2;

/**
 * A view on a list of records, the columns contained and potentially errors or messages. Instances of this record are
 * potentially mutable as we want to avoid creating copies of potentially large lists during construction. Please refrain
 * from changing the contents.
 *
 * @author Michael J. Simons
 * @param columns   The columns of the result set (only non-null if shape contained ROW)
 * @param data      The actual data (only non-null if shape contained ROW)
 * @param graph     Optional graph format
 * @param exception An optional exception. If it is {@literal null}, both {@link #columns} and {@link #data} will not be empty
 * @soundtrack Nightwish - Decades: Live In Buenos Aires
 */
public record EagerResult(List<String> columns, List<Record> data, Map<String, Collection<Map<String, Object>>> graph, Neo4jException exception) {

	static EagerResult success(Tuple2<List<String>, List<Record>> content, Set<AnnotatedQuery.ResultFormat> shape, TypeSystem typeSystem) {

		var columns = List.<String>of();
		var data = List.<Record>of();
		var graph = Map.<String, Collection<Map<String, Object>>>of();
		if (shape.contains(AnnotatedQuery.ResultFormat.ROW)) {
			columns = content.getT1();
			data = content.getT2();
		}

		if (shape.contains(AnnotatedQuery.ResultFormat.GRAPH)) {
			graph = buildGraphModel(content, typeSystem);
		}

		return new EagerResult(columns, data, graph, null);
	}

	private static Map<String, Collection<Map<String, Object>>> buildGraphModel(Tuple2<List<String>, List<Record>> content, TypeSystem typeSystem) {

		var graph = new HashMap<String, Collection<Map<String, Object>>>();
		var nodes = new HashMap<Long, Map<String, Object>>();
		var relationships = new HashMap<Long, Map<String, Object>>();
		for (var row : content.getT2()) {
			for (var column : row.values()) {
				if (column.hasType(typeSystem.NODE())) {
					handleNode(nodes, column.asNode());
				} else if (column.hasType(typeSystem.RELATIONSHIP())) {
					handleRelationship(relationships, column.asRelationship());
				} else if (column.hasType(typeSystem.PATH())) {
					var path = column.asPath();
					for (var segment : path) {
						handleNode(nodes, segment.start());
						handleRelationship(relationships, segment.relationship());
					}
					handleNode(nodes, path.end());
				}
			}
		}
		graph.put("nodes", nodes.values());
		graph.put("relationships", relationships.values());
		return graph;
	}

	@SuppressWarnings("deprecation")
	private static void handleNode(HashMap<Long, Map<String, Object>> nodes, Node node) {
		var id = node.id();
		if (nodes.containsKey(id)) {
			return;
		}
		nodes.put(id, Map.of(
			"id", id,
			"labels", StreamSupport.stream(node.labels().spliterator(), false).toArray(String[]::new),
			"properties", node.asMap(Function.identity())
		));
	}

	@SuppressWarnings("deprecation")
	private static void handleRelationship(HashMap<Long, Map<String, Object>> relationships, Relationship rel) {
		var id = rel.id();
		if (relationships.containsKey(id)) {
			return;
		}
		relationships.put(id, Map.of(
			"id", id,
			"type", rel.type(),
			"properties", rel.asMap(Function.identity())
		));
	}

	static EagerResult error(Neo4jException exception) {
		return new EagerResult(List.of(), List.of(), Map.of(), exception);
	}

	boolean isError() {
		return exception != null;
	}
}
