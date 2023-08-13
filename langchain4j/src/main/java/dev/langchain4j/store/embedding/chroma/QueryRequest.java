package dev.langchain4j.store.embedding.chroma;

import java.util.List;

import static java.util.Arrays.asList;

public class QueryRequest {

    //TODO where={"metadata_field": "is_equal_to_this"}, # optional filter
    //TODO   # where_document={"$contains":"search_string"}  # optional filter
    private final List<List<Float>> query_embeddings;
    private final int n_results;
    private final List<String> include;

    public QueryRequest(Builder builder) {
        this.query_embeddings = builder.queryEmbedding;
        this.n_results = builder.nResults;
        this.include = builder.include;
    }

    @Override
    public String toString() {
        return "QueryRequest{" +
                " queryEmbedding=" + query_embeddings +
                ", nResults=" + n_results +
                ", include=" + include +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<List<Float>> queryEmbedding;
        private int nResults = 10;
        private List<String> include = asList("metadatas", "documents", "distances", "embeddings");

        public Builder queryEmbedding(List<List<Float>> queryEmbedding) {
            this.queryEmbedding = queryEmbedding;
            return this;
        }

        public Builder nResults(int nResults) {
            this.nResults = nResults;
            return this;
        }

        QueryRequest build() {
            return new QueryRequest(this);
        }
    }

}
