package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiUsage.MistralAiUsageBuilder.class)
public class MistralAiUsage {

    private final Integer promptTokens;
    private final Integer totalTokens;
    private final Integer completionTokens;

    private MistralAiUsage(MistralAiUsageBuilder builder) {
        this.completionTokens = builder.completionTokens;
        this.promptTokens = builder.promptTokens;
        this.totalTokens = builder.totalTokens;
    }

    public Integer getPromptTokens() {
        return this.promptTokens;
    }

    public Integer getTotalTokens() {
        return this.totalTokens;
    }

    public Integer getCompletionTokens() {
        return this.completionTokens;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.promptTokens);
        hash = 89 * hash + Objects.hashCode(this.totalTokens);
        hash = 89 * hash + Objects.hashCode(this.completionTokens);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiUsage other = (MistralAiUsage) obj;
        return Objects.equals(this.promptTokens, other.promptTokens)
                && Objects.equals(this.totalTokens, other.totalTokens)
                && Objects.equals(this.completionTokens, other.completionTokens);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiUsage [", "]")
                .add("promptTokens=" + this.getPromptTokens())
                .add("totalTokens=" + this.getTotalTokens())
                .add("completionTokens=" + this.getCompletionTokens())
                .toString();
    }

    public static MistralAiUsageBuilder builder() {
        return new MistralAiUsageBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiUsageBuilder {

        private Integer promptTokens;
        private Integer totalTokens;
        private Integer completionTokens;

        public MistralAiUsageBuilder promptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public MistralAiUsageBuilder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public MistralAiUsageBuilder completionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public MistralAiUsage build() {
            return new MistralAiUsage(this);
        }
    }
}
