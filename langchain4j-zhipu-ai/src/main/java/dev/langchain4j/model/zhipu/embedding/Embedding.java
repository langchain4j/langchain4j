package dev.langchain4j.model.zhipu.embedding;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

import static java.util.Collections.unmodifiableList;

@Getter
@ToString
@EqualsAndHashCode
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
