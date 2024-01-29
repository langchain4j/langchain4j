package dev.langchain4j.model.zhipu.embedding;

import dev.langchain4j.model.zhipu.shared.Usage;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.unmodifiableList;

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

    public String getModel() {
        return model;
    }

    public String getObject() {
        return object;
    }

    public List<Embedding> getData() {
        return data;
    }

    public Usage getUsage() {
        return usage;
    }

    /**
     * Convenience method to get the embedding from the first data.
     */
    public List<Float> getEmbedding() {
        return data.get(0).getEmbedding();
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof EmbeddingResponse
                && equalTo((EmbeddingResponse) another);
    }

    private boolean equalTo(EmbeddingResponse another) {
        return Objects.equals(model, another.model)
                && Objects.equals(data, another.data)
                && Objects.equals(object, another.object)
                && Objects.equals(usage, another.usage);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(object);
        h += (h << 5) + Objects.hashCode(data);
        h += (h << 5) + Objects.hashCode(usage);
        return h;
    }

    @Override
    public String toString() {
        return "EmbeddingResponse{"
                + "model=" + model
                + ", object=" + object
                + ", data=" + data
                + ", usage=" + usage
                + "}";
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
