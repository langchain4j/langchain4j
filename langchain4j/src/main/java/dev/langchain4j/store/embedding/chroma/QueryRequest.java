package dev.langchain4j.store.embedding.chroma;

import java.util.List;

import static java.util.Arrays.asList;

class QueryRequest {

    private final List<List<Float>> queryEmbeddings;
    private final int nResults;
    private final List<String> include;

    public QueryRequest(Builder builder) {
        this.queryEmbeddings = builder.queryEmbedding;
        this.nResults = builder.nResults;
        this.include = builder.include;
    }

    @Override
    public String toString() {
        return "QueryRequest{" +
                " queryEmbedding=" + queryEmbeddings +
                ", nResults=" + nResults +
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
