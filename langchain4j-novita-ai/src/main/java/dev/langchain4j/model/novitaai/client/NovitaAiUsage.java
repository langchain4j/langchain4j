package dev.langchain4j.model.novitaai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;
import java.util.StringJoiner;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = NovitaAiUsage.NovitaAiUsageBuilder.class)
public class NovitaAiUsage {

    private final Integer promptTokens;
    private final Integer totalTokens;
    private final Integer completionTokens;

    private NovitaAiUsage(NovitaAiUsageBuilder builder) {
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
        final NovitaAiUsage other = (NovitaAiUsage) obj;
        return Objects.equals(this.promptTokens, other.promptTokens)
                && Objects.equals(this.totalTokens, other.totalTokens)
                && Objects.equals(this.completionTokens, other.completionTokens);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "NovitaAiUsage [", "]")
                .add("promptTokens=" + this.getPromptTokens())
                .add("totalTokens=" + this.getTotalTokens())
                .add("completionTokens=" + this.getCompletionTokens())
                .toString();
    }

    public static NovitaAiUsageBuilder builder() {
        return new NovitaAiUsageBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class NovitaAiUsageBuilder {

        private Integer promptTokens;
        private Integer totalTokens;
        private Integer completionTokens;

        public NovitaAiUsageBuilder promptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public NovitaAiUsageBuilder totalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public NovitaAiUsageBuilder completionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public NovitaAiUsage build() {
            return new NovitaAiUsage(this);
        }
    }
}
