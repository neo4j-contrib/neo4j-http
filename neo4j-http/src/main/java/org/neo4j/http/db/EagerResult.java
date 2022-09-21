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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.TypeSystem;
import org.neo4j.http.db.AnnotatedQuery.ResultFormat;

import reactor.util.function.Tuple3;

/**
 * A view on a list of records, the columns contained and potentially errors or messages. Instances of this record are
 * potentially mutable as we want to avoid creating copies of potentially large lists during construction. Please refrain
 * from changing the contents.
 *
 * @author Michael J. Simons
 * @param columns   The columns of the result set (only non-null if shape contained ROW)
 * @param data      The actual data
 * @param stats     Optional counters
 * @param exception An optional exception. If it is {@literal null}, both {@link #columns} and {@link #data} will not be empty
 * @soundtrack Nightwish - Decades: Live In Buenos Aires
 */
public record EagerResult(List<String> columns, List<ResultData> data, SummaryCounters stats, Neo4jException exception) {

	/**
	 * A wrapping structure for the odd shape of things in the old api
	 *
	 * @param records Plain list of records
	 * @param graph   Optional graph shaped values
	 * @param rest    Optional rest-ish shaped values (no hyperlinks)
	 */
	public record ResultData(Record records, Map<String, Collection<Map<String, Object>>> graph, List<Object> rest) {
	}

	static EagerResult success(Tuple3<List<String>, List<Record>, ResultSummary> content, boolean includeStats, Set<ResultFormat> shape, TypeSystem typeSystem) {

		List<ResultData> resultData = new ArrayList<>();
		for (Record record : content.getT2()) {
			resultData.add(new ResultData(
				shape.contains(ResultFormat.ROW) ? record : null,
				shape.contains(ResultFormat.GRAPH) ? buildGraphModel(record, typeSystem) : null,
				shape.contains(ResultFormat.REST) ? buildRestModel(record, typeSystem) : null
			));
		}

		List<String> columns = content.getT1();
		return new EagerResult(columns, resultData, includeStats ? content.getT3().counters() : null, null);
	}

	private static Map<String, Collection<Map<String, Object>>> buildGraphModel(Record row, TypeSystem typeSystem) {

		var graph = new HashMap<String, Collection<Map<String, Object>>>();
		var nodes = new HashMap<Long, Map<String, Object>>();
		var relationships = new HashMap<Long, Map<String, Object>>();

		for (var column : row.values()) {
			handleValue(typeSystem, nodes, relationships, column);
		}

		graph.put("nodes", nodes.values());
		graph.put("relationships", relationships.values());
		return graph;
	}

	private static void handleValue(TypeSystem typeSystem, HashMap<Long, Map<String, Object>> nodes, HashMap<Long, Map<String, Object>> relationships, Value column) {
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
		} else if (column.hasType(typeSystem.LIST())) {
			for (Value elem : column.values()) {
				handleValue(typeSystem, nodes, relationships, elem);
			}
		} else if (column.hasType(typeSystem.MAP())) {
			for (Value elem : column.asMap(Function.identity()).values()) {
				handleValue(typeSystem, nodes, relationships, elem);
			}
		}
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
			"properties", rel.asMap(Function.identity()),
			"startNode", rel.startNodeId(),
			"endNode", rel.endNodeId()
		));
	}

	@SuppressWarnings("deprecation")
	private static List<Object> buildRestModel(Record row, TypeSystem typeSystem) {

		var rest = new ArrayList<>();
		for (var column : row.values()) {
			if (column.hasType(typeSystem.NODE())) {
				var node = column.asNode();
				Map<String, Object> metaData = new HashMap<>();
				metaData.put("id", node.id());
				metaData.put("labels", StreamSupport.stream(node.labels().spliterator(), false).toArray(String[]::new));
				rest.add(Map.of("metadata", metaData, "data", node.asMap(Function.identity())));
			} else if (column.hasType(typeSystem.RELATIONSHIP())) {
				var rel = column.asRelationship();
				Map<String, Object> metaData = new HashMap<>();
				metaData.put("id", rel.id());
				metaData.put("type", rel.type());
				rest.add(Map.of("metadata", metaData, "data", rel.asMap(Function.identity())));
			} else if (column.hasType(typeSystem.PATH())) {
				throw new UnsupportedOperationException("Paths are not supported with REST shape");
			} else {
				rest.add(column);
			}
		}
		return rest;
	}

	static EagerResult error(Neo4jException exception) {
		return new EagerResult(null, null, null, exception);
	}

	boolean isError() {
		return exception != null;
	}
}
