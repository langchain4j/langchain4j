package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;
import java.util.List;
import java.util.Map;

@Internal
class DeleteEmbeddingsRequest {

    private final List<String> ids;
    private final Map<String, Object> where;

    private DeleteEmbeddingsRequest(Builder builder) {
        this.ids = builder.ids;
        this.where = builder.where;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<String> getIds() {
        return ids;
    }

    public Map<String, Object> getWhere() {
        return where;
    }

    public static class Builder {

        private List<String> ids;
        private Map<String, Object> where;

        public Builder ids(List<String> ids) {
            this.ids = ids;
            return this;
        }

        public Builder where(Map<String, Object> where) {
            this.where = where;
            return this;
        }

        public DeleteEmbeddingsRequest build() {
            return new DeleteEmbeddingsRequest(this);
        }
    }
}
