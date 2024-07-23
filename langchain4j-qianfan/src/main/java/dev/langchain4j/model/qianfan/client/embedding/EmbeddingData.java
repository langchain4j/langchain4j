package dev.langchain4j.model.qianfan.client.embedding;


import java.util.Collections;
import java.util.List;

public final class EmbeddingData {

    private final String object;
    private final List<Float> embedding;
    private final Integer index;

    private EmbeddingData(Builder builder) {
        this.embedding = builder.embedding;
        this.index = builder.index;
        this.object = builder.object;
    }

    public List<Float> embedding() {
        return this.embedding;
    }

    public Integer index() {
        return this.index;
    }

    public String object() {
        return this.object;
    }

    @Override
    public String toString() {
        return "EmbeddingData{" +
                "object='" + object + '\'' +
                ", embedding=" + embedding +
                ", index=" + index +
                '}';
    }


    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private List<Float> embedding;
        private Integer index;
        private String object;

        private Builder() {
        }

        public Builder embedding(List<Float> embedding) {
            if (embedding != null) {
                this.embedding = Collections.unmodifiableList(embedding);
            }
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }
        public Builder object(String object) {
            this.object = object;
            return this;
        }
        public EmbeddingData build() {
            return new EmbeddingData(this);
        }
    }
}

