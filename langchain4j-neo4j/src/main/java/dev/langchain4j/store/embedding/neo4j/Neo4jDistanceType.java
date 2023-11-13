package dev.langchain4j.store.embedding.neo4j;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Neo4jDistanceType {
    /* cosine similarity */
    COSINE("cosine"),
    
    /* euclidean distance */
    EUCLIDEAN("euclidean");
    
    private final String value;
}