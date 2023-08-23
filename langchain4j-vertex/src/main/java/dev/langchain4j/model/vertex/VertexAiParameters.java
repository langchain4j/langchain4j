package dev.langchain4j.model.vertex;

import java.util.Objects;

class VertexAiParameters {

    private final Double temperature;
    private final Integer maxOutputTokens;
    private final Integer topK;
    private final Double topP;

    VertexAiParameters(Double temperature,
                       Integer maxOutputTokens,
                       Integer topK,
                       Double topP) {
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.topK = topK;
        this.topP = topP;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof VertexAiParameters
                && equalTo((VertexAiParameters) another);
    }

    private boolean equalTo(VertexAiParameters another) {
        return
                Objects.equals(temperature, another.temperature)
                        && Objects.equals(maxOutputTokens, another.maxOutputTokens)
                        && Objects.equals(topK, another.topK)
                        && Objects.equals(topP, another.topP);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(temperature);
        h += (h << 5) + Objects.hashCode(maxOutputTokens);
        h += (h << 5) + Objects.hashCode(topK);
        h += (h << 5) + Objects.hashCode(topP);
        return h;
    }

    @Override
    public String toString() {
        return "TextGenerationRequest {"
                + " temperature = " + temperature
                + ", maxNewTokens = " + maxOutputTokens
                + ", topK = " + topK
                + ", topP = " + topP
                + " }";
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private Double temperature = 0.0;
        private Integer maxOutputTokens = 0;
        private Integer topK = 40;
        private Double topP = 0.95;

        Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        VertexAiParameters build() {
            return new VertexAiParameters(this.temperature, this.maxOutputTokens, this.topK, this.topP);
        }
    }

}
