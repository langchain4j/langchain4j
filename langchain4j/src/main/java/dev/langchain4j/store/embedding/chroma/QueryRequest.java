package dev.langchain4j.store.embedding.chroma;

import java.util.List;

import static java.util.Arrays.asList;

class QueryRequest {

    private final List<List<Float>> queryEmbeddings;
    private final int nResults;
    private final List<String> include = asList("metadatas", "documents", "distances", "embeddings");

    public QueryRequest(List<List<Float>> queryEmbeddings, int nResults) {
        this.queryEmbeddings = queryEmbeddings;
        this.nResults = nResults;
    }

}
