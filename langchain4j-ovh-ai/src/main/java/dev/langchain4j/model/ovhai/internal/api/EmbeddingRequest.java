package dev.langchain4j.model.ovhai.internal.api;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public class EmbeddingRequest {
    @JsonValue
    private List<String> input;

    public EmbeddingRequest(List<String> input) {
        this.input = input;
    }

    public EmbeddingRequest() {
    }

    public static EmbeddingRequestBuilder builder() {
        return new EmbeddingRequestBuilder();
    }

    public List<String> getInput() {
        return this.input;
    }

    public void setInput(List<String> input) {
        this.input = input;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof EmbeddingRequest)) return false;
        final EmbeddingRequest other = (EmbeddingRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$input = this.getInput();
        final Object other$input = other.getInput();
        if (this$input == null ? other$input != null : !this$input.equals(other$input)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof EmbeddingRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $input = this.getInput();
        result = result * PRIME + ($input == null ? 43 : $input.hashCode());
        return result;
    }

    public String toString() {
        return "EmbeddingRequest(input=" + this.getInput() + ")";
    }

    public static class EmbeddingRequestBuilder {
        private List<String> input;

        EmbeddingRequestBuilder() {
        }

        public EmbeddingRequestBuilder input(List<String> input) {
            this.input = input;
            return this;
        }

        public EmbeddingRequest build() {
            return new EmbeddingRequest(this.input);
        }

        public String toString() {
            return "EmbeddingRequest.EmbeddingRequestBuilder(input=" + this.input + ")";
        }
    }
}
