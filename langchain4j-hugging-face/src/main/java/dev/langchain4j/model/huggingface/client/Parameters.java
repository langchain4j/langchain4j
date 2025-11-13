package dev.langchain4j.model.huggingface.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Deprecated(forRemoval = true, since = "1.7.0-beta13")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class Parameters {

    private final Integer topK;
    private final Double topP;
    private final Double temperature;
    private final Double repetitionPenalty;
    private final Integer maxNewTokens;
    private final Double maxTime;
    private final Boolean returnFullText;
    private final Integer numReturnSequences;
    private final Boolean doSample;

    public Parameters(Builder builder) {
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.temperature = builder.temperature;
        this.repetitionPenalty = builder.repetitionPenalty;
        this.maxNewTokens = builder.maxNewTokens;
        this.maxTime = builder.maxTime;
        this.returnFullText = builder.returnFullText;
        this.numReturnSequences = builder.numReturnSequences;
        this.doSample = builder.doSample;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Parameters
                && equalTo((Parameters) another);
    }

    private boolean equalTo(Parameters another) {
        return Objects.equals(topK, another.topK)
                && Objects.equals(topP, another.topP)
                && Objects.equals(temperature, another.temperature)
                && Objects.equals(repetitionPenalty, another.repetitionPenalty)
                && Objects.equals(maxNewTokens, another.maxNewTokens)
                && Objects.equals(maxTime, another.maxTime)
                && Objects.equals(returnFullText, another.returnFullText)
                && Objects.equals(numReturnSequences, another.numReturnSequences)
                && Objects.equals(doSample, another.doSample);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(topK);
        h += (h << 5) + Objects.hashCode(topP);
        h += (h << 5) + Objects.hashCode(temperature);
        h += (h << 5) + Objects.hashCode(repetitionPenalty);
        h += (h << 5) + Objects.hashCode(maxNewTokens);
        h += (h << 5) + Objects.hashCode(maxTime);
        h += (h << 5) + Objects.hashCode(returnFullText);
        h += (h << 5) + Objects.hashCode(numReturnSequences);
        h += (h << 5) + Objects.hashCode(doSample);
        return h;
    }

    @Override
    public String toString() {
        return "TextGenerationRequest {"
                + " topK = " + topK
                + ", topP = " + topP
                + ", temperature = " + temperature
                + ", repetitionPenalty = " + repetitionPenalty
                + ", maxNewTokens = " + maxNewTokens
                + ", maxTime = " + maxTime
                + ", returnFullText = " + returnFullText
                + ", numReturnSequences = " + numReturnSequences
                + ", doSample = " + doSample
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Integer topK;
        private Double topP;
        private Double temperature;
        private Double repetitionPenalty;
        private Integer maxNewTokens;
        private Double maxTime;
        private Boolean returnFullText;
        private Integer numReturnSequences;
        private Boolean doSample;

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder repetitionPenalty(Double repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public Builder maxNewTokens(Integer maxNewTokens) {
            this.maxNewTokens = maxNewTokens;
            return this;
        }

        public Builder maxTime(Double maxTime) {
            this.maxTime = maxTime;
            return this;
        }

        public Builder returnFullText(Boolean returnFullText) {
            this.returnFullText = returnFullText;
            return this;
        }

        public Builder numReturnSequences(Integer numReturnSequences) {
            this.numReturnSequences = numReturnSequences;
            return this;
        }

        public Builder doSample(Boolean doSample) {
            this.doSample = doSample;
            return this;
        }

        public Parameters build() {
            return new Parameters(this);
        }
    }
}
