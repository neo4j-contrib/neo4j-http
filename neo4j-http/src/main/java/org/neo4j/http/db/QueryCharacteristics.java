package org.neo4j.http.db;

public record QueryCharacteristics(boolean callInTx, boolean periodicCommit) {
}
