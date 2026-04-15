package dev.langchain4j.model.openai;

import java.util.List;
import java.util.Objects;

public final class LogProb {

    private final String token;
    private final Double logprob;
    private final List<Integer> bytes;
    private final List<LogProb> topLogprobs;

    private LogProb(Builder builder) {
        this.token = builder.token;
        this.logprob = builder.logprob;
        this.bytes = builder.bytes;
        this.topLogprobs = builder.topLogprobs;
    }

    public String token() {
        return token;
    }

    public Double logprob() {
        return logprob;
    }

    public List<Integer> bytes() {
        return bytes;
    }

    public List<LogProb> topLogprobs() {
        return topLogprobs;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof LogProb && equalTo((LogProb) another);
    }

    private boolean equalTo(LogProb another) {
        return Objects.equals(token, another.token)
                && Objects.equals(logprob, another.logprob)
                && Objects.equals(bytes, another.bytes)
                && Objects.equals(topLogprobs, another.topLogprobs);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(token);
        h += (h << 5) + Objects.hashCode(logprob);
        h += (h << 5) + Objects.hashCode(bytes);
        h += (h << 5) + Objects.hashCode(topLogprobs);
        return h;
    }

    @Override
    public String toString() {
        return "LogProb{"
                + "token=" + token
                + ", logprob=" + logprob
                + ", bytes=" + bytes
                + ", topLogprobs=" + topLogprobs
                + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String token;
        private Double logprob;
        private List<Integer> bytes;
        private List<LogProb> topLogprobs;

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder logprob(Double logprob) {
            this.logprob = logprob;
            return this;
        }

        public Builder bytes(List<Integer> bytes) {
            this.bytes = bytes;
            return this;
        }

        public Builder topLogprobs(List<LogProb> topLogprobs) {
            this.topLogprobs = topLogprobs;
            return this;
        }

        public LogProb build() {
            return new LogProb(this);
        }
    }
}
