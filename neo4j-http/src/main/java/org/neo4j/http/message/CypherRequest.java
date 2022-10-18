package org.neo4j.http.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record CypherRequest(List<StatementAndParameter> statements) {
	public static class StatementAndParameter {
		@JsonProperty("statement")
		private final String statement;
		@JsonProperty("parameters")
		private final Parameters parameters;

		@JsonCreator
		public StatementAndParameter(@JsonProperty("statement") String statement, @JsonProperty("parameters") Parameters parameters) {
			this.statement = statement;
			this.parameters = parameters;
		}

		public String statement() {
			return statement;
		}

		public Map<String, Object> parameters() {
			return parameters.targetMap;
		}
	}

	public static class Parameters {

		public final Map<String, Object> targetMap;

		@JsonCreator
		public Parameters(Map<String, Object> targetMap) {
			this.targetMap = targetMap.entrySet().stream().collect(Collectors.toMap(
					Map.Entry::getKey,
					map -> {
						Object newValue = map.getValue();
						newValue = optionalConversion(newValue);
						return newValue;
					}));
		}

		private Object optionalConversion(Object newValue) {
			if (newValue instanceof Map<?, ?> valueMap) {
				Object typeValue = valueMap.get("type");
				if (typeValue != null) {
					String customTypeValue = valueMap.get("value").toString();
					newValue = ParameterConverter.CONVERTER.apply((String) typeValue, customTypeValue);
				}
			}
			return newValue;
		}
	}

}

