package dev.langchain4j.model.zhipu.embedding;

import dev.langchain4j.model.zhipu.shared.Usage;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

import static java.util.Collections.unmodifiableList;

@Getter
@ToString
@EqualsAndHashCode
public final class EmbeddingResponse {
    private final String model;
    private final String object;
    private final List<Embedding> data;
    private final Usage usage;

    private EmbeddingResponse(Builder builder) {
        this.model = builder.model;
        this.object = builder.object;
        this.data = builder.data;
        this.usage = builder.usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience method to get the embedding from the first data.
     */
    public List<Float> getEmbedding() {
        return data.get(0).getEmbedding();
    }

    public static final class Builder {

        private String model;
        private String object;
        private List<Embedding> data;
        private Usage usage;

        private Builder() {
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder data(List<Embedding> data) {
            if (data != null) {
                this.data = unmodifiableList(data);
            }
            return this;
        }

        public Builder usage(Usage usage) {
            this.usage = usage;
            return this;
        }

        public EmbeddingResponse build() {
            return new EmbeddingResponse(this);
        }
    }
}
