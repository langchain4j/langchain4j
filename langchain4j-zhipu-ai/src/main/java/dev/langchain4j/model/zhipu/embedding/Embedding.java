package dev.langchain4j.model.zhipu.embedding;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.unmodifiableList;

public final class Embedding {

    private final List<Float> embedding;
    private final String object;
    private final Integer index;

    private Embedding(Builder builder) {
        this.embedding = builder.embedding;
        this.object = builder.object;
        this.index = builder.index;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public String getObject() {
        return object;
    }

    public Integer getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Embedding
                && equalTo((Embedding) another);
    }

    private boolean equalTo(Embedding another) {
        return Objects.equals(embedding, another.embedding)
                && Objects.equals(object, another.object)
                && Objects.equals(index, another.index);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(embedding);
        h += (h << 5) + Objects.hashCode(object);
        h += (h << 5) + Objects.hashCode(index);
        return h;
    }

    @Override
    public String toString() {
        return "Embedding{"
                + "embedding=" + embedding
                + ", object=" + object
                + ", index=" + index
                + "}";
    }

    public static final class Builder {

        private List<Float> embedding;
        private String object;
        private Integer index;

        private Builder() {
        }

        public Builder embedding(List<Float> embedding) {
            if (embedding != null) {
                this.embedding = unmodifiableList(embedding);
            }
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Embedding build() {
            return new Embedding(this);
        }
    }
}
