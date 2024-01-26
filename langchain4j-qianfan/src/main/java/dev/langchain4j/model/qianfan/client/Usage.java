package dev.langchain4j.model.qianfan.client;

import java.util.Objects;

public final class Usage {
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;

    private Usage(Builder builder) {
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.totalTokens = builder.totalTokens;
    }

    public Integer promptTokens() {
        return this.promptTokens;
    }

    public Integer completionTokens() {
        return this.completionTokens;
    }

    public Integer totalTokens() {
        return this.totalTokens;
    }

    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof Usage && this.equalTo((Usage)another);
        }
    }

    private boolean equalTo(Usage another) {
        return Objects.equals(this.promptTokens, another.promptTokens) && Objects.equals(this.completionTokens, another.completionTokens) && Objects.equals(this.totalTokens, another.totalTokens);
    }

    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.promptTokens);
        h += (h << 5) + Objects.hashCode(this.completionTokens);
        h += (h << 5) + Objects.hashCode(this.totalTokens);
        return h;
    }

    public String toString() {
        return "Usage{promptTokens=" + this.promptTokens + ", completionTokens=" + this.completionTokens + ", totalTokens=" + this.totalTokens + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;

        private Builder() {
        }

        public Builder promptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Usage build() {
            return new Usage(this);
        }
    }
}

