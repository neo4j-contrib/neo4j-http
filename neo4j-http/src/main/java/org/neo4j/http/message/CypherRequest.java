package org.neo4j.http.message;

import java.util.List;
import java.util.Map;

public record CypherRequest(List<StatementAndParameter> statements) {

	public record StatementAndParameter(String statement, Map<String, Object> parameters) {

	}

}