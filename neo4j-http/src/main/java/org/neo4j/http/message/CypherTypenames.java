package org.neo4j.http.message;

import javax.management.relation.Relation;

import org.neo4j.driver.types.Relationship;

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
