package dev.langchain4j.model.zhipu.shared;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class Usage {
    @SerializedName("prompt_tokens")
    private Integer promptTokens;
    @SerializedName("completion_tokens")
    private Integer completionTokens;
    @SerializedName("total_tokens")
    private Integer totalTokens;

    private Usage(Builder builder) {
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.totalTokens = builder.totalTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void add(Usage usage) {
        this.promptTokens += usage.promptTokens;
        this.completionTokens += usage.completionTokens;
        this.totalTokens += usage.totalTokens;
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
