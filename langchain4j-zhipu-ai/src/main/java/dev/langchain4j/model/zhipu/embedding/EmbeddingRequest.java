package dev.langchain4j.model.zhipu.embedding;

import java.util.List;
import java.util.Objects;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public final class EmbeddingRequest {
    private final String input;
    private final String model;

    private EmbeddingRequest(Builder builder) {
        this.model = builder.model;
        this.input = builder.input.get(0);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getInput() {
        return input;
    }

    public String getModel() {
        return model;
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
        private List<String> input;

        private Builder() {
        }

        public Builder model(EmbeddingModel model) {
            return model(model.toString());
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder input(String... input) {
            return input(asList(input));
        }

        public Builder input(List<String> input) {
            if (input == null || input.isEmpty()) {
                throw new RuntimeException();
            }
            if (input.size() > 1) {
                throw new RuntimeException();
            }
            this.input = unmodifiableList(input);
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}
