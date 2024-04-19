package dev.langchain4j.model.zhipu.embedding;

import lombok.Getter;

import java.util.Objects;

@Getter
public final class EmbeddingRequest {
    private final String input;
    private final String model;

    private EmbeddingRequest(Builder builder) {
        this.model = builder.model;
        this.input = builder.input;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof EmbeddingRequest
                && equalTo((EmbeddingRequest) another);
    }

    private boolean equalTo(EmbeddingRequest another) {
        return Objects.equals(model, another.model)
                && Objects.equals(input, another.input);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(model);
        h += (h << 5) + Objects.hashCode(input);
        return h;
    }

    @Override
    public String toString() {
        return "EmbeddingRequest{"
                + "model=" + model
                + ", input=" + input
                + "}";
    }

    public static final class Builder {

        private String model = EmbeddingModel.EMBEDDING_2.toString();
        private String input;

        private Builder() {
        }

        public Builder model(EmbeddingModel model) {
            return model(model.toString());
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder input(String input) {
            this.input = input;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}
