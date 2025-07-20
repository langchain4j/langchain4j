package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiAttributionSourceId {
    private GeminiGroundingPassageId groundingPassage;
    private GeminiSemanticRetrieverChunk semanticRetrieverChunk;

    @JsonCreator
    GeminiAttributionSourceId(@JsonProperty("groundingPassage") GeminiGroundingPassageId groundingPassage,
                              @JsonProperty("semanticRetrieverChunk") GeminiSemanticRetrieverChunk semanticRetrieverChunk) {
        this.groundingPassage = groundingPassage;
        this.semanticRetrieverChunk = semanticRetrieverChunk;
    }

    public static GeminiAttributionSourceIdBuilder builder() {
        return new GeminiAttributionSourceIdBuilder();
    }

    public GeminiGroundingPassageId getGroundingPassage() {
        return this.groundingPassage;
    }

    public GeminiSemanticRetrieverChunk getSemanticRetrieverChunk() {
        return this.semanticRetrieverChunk;
    }

    public void setGroundingPassage(GeminiGroundingPassageId groundingPassage) {
        this.groundingPassage = groundingPassage;
    }

    public void setSemanticRetrieverChunk(GeminiSemanticRetrieverChunk semanticRetrieverChunk) {
        this.semanticRetrieverChunk = semanticRetrieverChunk;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiAttributionSourceId)) return false;
        final GeminiAttributionSourceId other = (GeminiAttributionSourceId) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$groundingPassage = this.getGroundingPassage();
        final Object other$groundingPassage = other.getGroundingPassage();
        if (this$groundingPassage == null ? other$groundingPassage != null : !this$groundingPassage.equals(other$groundingPassage))
            return false;
        final Object this$semanticRetrieverChunk = this.getSemanticRetrieverChunk();
        final Object other$semanticRetrieverChunk = other.getSemanticRetrieverChunk();
        if (this$semanticRetrieverChunk == null ? other$semanticRetrieverChunk != null : !this$semanticRetrieverChunk.equals(other$semanticRetrieverChunk))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiAttributionSourceId;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $groundingPassage = this.getGroundingPassage();
        result = result * PRIME + ($groundingPassage == null ? 43 : $groundingPassage.hashCode());
        final Object $semanticRetrieverChunk = this.getSemanticRetrieverChunk();
        result = result * PRIME + ($semanticRetrieverChunk == null ? 43 : $semanticRetrieverChunk.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiAttributionSourceId(groundingPassage=" + this.getGroundingPassage() + ", semanticRetrieverChunk=" + this.getSemanticRetrieverChunk() + ")";
    }

    public static class GeminiAttributionSourceIdBuilder {
        private GeminiGroundingPassageId groundingPassage;
        private GeminiSemanticRetrieverChunk semanticRetrieverChunk;

        GeminiAttributionSourceIdBuilder() {
        }

        public GeminiAttributionSourceIdBuilder groundingPassage(GeminiGroundingPassageId groundingPassage) {
            this.groundingPassage = groundingPassage;
            return this;
        }

        public GeminiAttributionSourceIdBuilder semanticRetrieverChunk(GeminiSemanticRetrieverChunk semanticRetrieverChunk) {
            this.semanticRetrieverChunk = semanticRetrieverChunk;
            return this;
        }

        public GeminiAttributionSourceId build() {
            return new GeminiAttributionSourceId(this.groundingPassage, this.semanticRetrieverChunk);
        }

        public String toString() {
            return "GeminiAttributionSourceId.GeminiAttributionSourceIdBuilder(groundingPassage=" + this.groundingPassage + ", semanticRetrieverChunk=" + this.semanticRetrieverChunk + ")";
        }
    }
}
