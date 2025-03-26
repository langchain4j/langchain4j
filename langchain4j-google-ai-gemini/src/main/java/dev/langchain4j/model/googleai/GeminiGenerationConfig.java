package dev.langchain4j.model.googleai;

import java.util.List;

class GeminiGenerationConfig {
    private List<String> stopSequences;
    private String responseMimeType;
    private GeminiSchema responseSchema;
    private Integer candidateCount = 1;
    private Integer maxOutputTokens = 8192;
    private Double temperature = 1.0;
    private Integer topK = 64;
    private Double topP = 0.95;

    GeminiGenerationConfig(List<String> stopSequences, String responseMimeType, GeminiSchema responseSchema, Integer candidateCount, Integer maxOutputTokens, Double temperature, Integer topK, Double topP) {
        this.stopSequences = stopSequences;
        this.responseMimeType = responseMimeType;
        this.responseSchema = responseSchema;
        this.candidateCount = candidateCount;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.topK = topK;
        this.topP = topP;
    }

    public static GeminiGenerationConfigBuilder builder() {
        return new GeminiGenerationConfigBuilder();
    }

    public List<String> getStopSequences() {
        return this.stopSequences;
    }

    public String getResponseMimeType() {
        return this.responseMimeType;
    }

    public GeminiSchema getResponseSchema() {
        return this.responseSchema;
    }

    public Integer getCandidateCount() {
        return this.candidateCount;
    }

    public Integer getMaxOutputTokens() {
        return this.maxOutputTokens;
    }

    public Double getTemperature() {
        return this.temperature;
    }

    public Integer getTopK() {
        return this.topK;
    }

    public Double getTopP() {
        return this.topP;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    public void setResponseMimeType(String responseMimeType) {
        this.responseMimeType = responseMimeType;
    }

    public void setResponseSchema(GeminiSchema responseSchema) {
        this.responseSchema = responseSchema;
    }

    public void setCandidateCount(Integer candidateCount) {
        this.candidateCount = candidateCount;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiGenerationConfig)) return false;
        final GeminiGenerationConfig other = (GeminiGenerationConfig) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$stopSequences = this.getStopSequences();
        final Object other$stopSequences = other.getStopSequences();
        if (this$stopSequences == null ? other$stopSequences != null : !this$stopSequences.equals(other$stopSequences))
            return false;
        final Object this$responseMimeType = this.getResponseMimeType();
        final Object other$responseMimeType = other.getResponseMimeType();
        if (this$responseMimeType == null ? other$responseMimeType != null : !this$responseMimeType.equals(other$responseMimeType))
            return false;
        final Object this$responseSchema = this.getResponseSchema();
        final Object other$responseSchema = other.getResponseSchema();
        if (this$responseSchema == null ? other$responseSchema != null : !this$responseSchema.equals(other$responseSchema))
            return false;
        final Object this$candidateCount = this.getCandidateCount();
        final Object other$candidateCount = other.getCandidateCount();
        if (this$candidateCount == null ? other$candidateCount != null : !this$candidateCount.equals(other$candidateCount))
            return false;
        final Object this$maxOutputTokens = this.getMaxOutputTokens();
        final Object other$maxOutputTokens = other.getMaxOutputTokens();
        if (this$maxOutputTokens == null ? other$maxOutputTokens != null : !this$maxOutputTokens.equals(other$maxOutputTokens))
            return false;
        final Object this$temperature = this.getTemperature();
        final Object other$temperature = other.getTemperature();
        if (this$temperature == null ? other$temperature != null : !this$temperature.equals(other$temperature))
            return false;
        final Object this$topK = this.getTopK();
        final Object other$topK = other.getTopK();
        if (this$topK == null ? other$topK != null : !this$topK.equals(other$topK)) return false;
        final Object this$topP = this.getTopP();
        final Object other$topP = other.getTopP();
        if (this$topP == null ? other$topP != null : !this$topP.equals(other$topP)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiGenerationConfig;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $stopSequences = this.getStopSequences();
        result = result * PRIME + ($stopSequences == null ? 43 : $stopSequences.hashCode());
        final Object $responseMimeType = this.getResponseMimeType();
        result = result * PRIME + ($responseMimeType == null ? 43 : $responseMimeType.hashCode());
        final Object $responseSchema = this.getResponseSchema();
        result = result * PRIME + ($responseSchema == null ? 43 : $responseSchema.hashCode());
        final Object $candidateCount = this.getCandidateCount();
        result = result * PRIME + ($candidateCount == null ? 43 : $candidateCount.hashCode());
        final Object $maxOutputTokens = this.getMaxOutputTokens();
        result = result * PRIME + ($maxOutputTokens == null ? 43 : $maxOutputTokens.hashCode());
        final Object $temperature = this.getTemperature();
        result = result * PRIME + ($temperature == null ? 43 : $temperature.hashCode());
        final Object $topK = this.getTopK();
        result = result * PRIME + ($topK == null ? 43 : $topK.hashCode());
        final Object $topP = this.getTopP();
        result = result * PRIME + ($topP == null ? 43 : $topP.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiGenerationConfig(stopSequences=" + this.getStopSequences() + ", responseMimeType=" + this.getResponseMimeType() + ", responseSchema=" + this.getResponseSchema() + ", candidateCount=" + this.getCandidateCount() + ", maxOutputTokens=" + this.getMaxOutputTokens() + ", temperature=" + this.getTemperature() + ", topK=" + this.getTopK() + ", topP=" + this.getTopP() + ")";
    }

    public static class GeminiGenerationConfigBuilder {
        private List<String> stopSequences;
        private String responseMimeType;
        private GeminiSchema responseSchema;
        private Integer candidateCount;
        private Integer maxOutputTokens;
        private Double temperature;
        private Integer topK;
        private Double topP;

        GeminiGenerationConfigBuilder() {
        }

        public GeminiGenerationConfigBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public GeminiGenerationConfigBuilder responseMimeType(String responseMimeType) {
            this.responseMimeType = responseMimeType;
            return this;
        }

        public GeminiGenerationConfigBuilder responseSchema(GeminiSchema responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        public GeminiGenerationConfigBuilder candidateCount(Integer candidateCount) {
            this.candidateCount = candidateCount;
            return this;
        }

        public GeminiGenerationConfigBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public GeminiGenerationConfigBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public GeminiGenerationConfigBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public GeminiGenerationConfigBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public GeminiGenerationConfig build() {
            return new GeminiGenerationConfig(this.stopSequences, this.responseMimeType, this.responseSchema, this.candidateCount, this.maxOutputTokens, this.temperature, this.topK, this.topP);
        }

        public String toString() {
            return "GeminiGenerationConfig.GeminiGenerationConfigBuilder(stopSequences=" + this.stopSequences + ", responseMimeType=" + this.responseMimeType + ", responseSchema=" + this.responseSchema + ", candidateCount=" + this.candidateCount + ", maxOutputTokens=" + this.maxOutputTokens + ", temperature=" + this.temperature + ", topK=" + this.topK + ", topP=" + this.topP + ")";
        }
    }
}
