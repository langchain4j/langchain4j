package dev.langchain4j.graph.neo4j;

public class Neo4jException extends RuntimeException {

    public Neo4jException(String message, Throwable cause) {

        super(message, cause);
    }
}
