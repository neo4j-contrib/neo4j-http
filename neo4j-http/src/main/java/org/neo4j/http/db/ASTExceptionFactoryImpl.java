package org.neo4j.http.db;

import java.util.List;

import org.neo4j.cypher.internal.ast.factory.ASTExceptionFactory;

public enum ASTExceptionFactoryImpl implements ASTExceptionFactory
{
    INSTANCE;

    @Override
    public Exception syntaxException( String got, List<String> expected, Exception source, int offset, int line, int column) {
        return new RuntimeException(source.getMessage());
    }

    @Override
    public Exception syntaxException(Exception source, int offset, int line, int column) {
        return new RuntimeException(source.getMessage());
    }
}
