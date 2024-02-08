package dev.langchain4j.model.qianfan.client.embedding;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class EmbeddingRequest {
    private final String model;
    private final List<String> input;
    private final String user;

    private EmbeddingRequest(Builder builder) {
        this.model = builder.model;
        this.input = builder.input;
        this.user = builder.user;
    }

    public String model() {
        return this.model;
    }

    public List<String> input() {
        return this.input;
    }

    public String user() {
        return this.user;
    }

    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof EmbeddingRequest
                    && this.equalTo((EmbeddingRequest)another);
        }
    }

    private boolean equalTo(EmbeddingRequest another) {
        return Objects.equals(this.model, another.model) && Objects.equals(this.input, another.input) && Objects.equals(this.user, another.user);
    }

    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.model);
        h += (h << 5) + Objects.hashCode(this.input);
        h += (h << 5) + Objects.hashCode(this.user);
        return h;
    }

    public String toString() {
        return "EmbeddingRequest{model=" + this.model + ", input=" + this.input + ", user=" + this.user + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String model;
        private List<String> input;
        private String user;

        private Builder() {

        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }



        public Builder input(String... input) {
            return this.input(Arrays.asList(input));
        }

        public Builder input(List<String> input) {
            if (input == null) {
                return this;
            } else {
                this.input = Collections.unmodifiableList(input);
                return this;
            }
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this);
        }
    }
}

