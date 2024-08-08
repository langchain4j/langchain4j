package dev.langchain4j.store.embedding.chroma;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import java.util.List;
import java.util.Map;

class QueryRequest {

    private final Map<String, Object> where;
    private final List<List<Float>> queryEmbeddings;
    private final int nResults;
    private final List<String> include;

    private QueryRequest(Builder builder) {
        this.where = builder.where;
        this.queryEmbeddings = builder.queryEmbeddings;
        this.nResults = builder.nResults;
        this.include = builder.include;
    }

    public static class Builder {

        private Map<String, Object> where;
        private List<List<Float>> queryEmbeddings;
        private int nResults;
        private List<String> include = asList("metadatas", "documents", "distances", "embeddings");

        public Builder where(Map<String, Object> where) {
            this.where = where;
            return this;
        }

        public Builder queryEmbeddings(List<Float> queryEmbeddings) {
            this.queryEmbeddings = singletonList(queryEmbeddings);
            return this;
        }

        public Builder nResults(int nResults) {
            this.nResults = nResults;
            return this;
        }

        public Builder include(List<String> include) {
            this.include = include;
            return this;
        }

        public QueryRequest build() {
            return new QueryRequest(this);
        }
    }
}
