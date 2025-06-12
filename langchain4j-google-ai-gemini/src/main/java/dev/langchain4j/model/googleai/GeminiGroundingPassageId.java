package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiGroundingPassageId {
    private String passageId;
    private String partIndex;

    @JsonCreator
    GeminiGroundingPassageId(@JsonProperty("passageId") String passageId, @JsonProperty("partIndex") String partIndex) {
        this.passageId = passageId;
        this.partIndex = partIndex;
    }

    public static GeminiGroundingPassageIdBuilder builder() {
        return new GeminiGroundingPassageIdBuilder();
    }

    public String getPassageId() {
        return this.passageId;
    }

    public String getPartIndex() {
        return this.partIndex;
    }

    public void setPassageId(String passageId) {
        this.passageId = passageId;
    }

    public void setPartIndex(String partIndex) {
        this.partIndex = partIndex;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiGroundingPassageId)) return false;
        final GeminiGroundingPassageId other = (GeminiGroundingPassageId) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$passageId = this.getPassageId();
        final Object other$passageId = other.getPassageId();
        if (this$passageId == null ? other$passageId != null : !this$passageId.equals(other$passageId)) return false;
        final Object this$partIndex = this.getPartIndex();
        final Object other$partIndex = other.getPartIndex();
        if (this$partIndex == null ? other$partIndex != null : !this$partIndex.equals(other$partIndex)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiGroundingPassageId;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $passageId = this.getPassageId();
        result = result * PRIME + ($passageId == null ? 43 : $passageId.hashCode());
        final Object $partIndex = this.getPartIndex();
        result = result * PRIME + ($partIndex == null ? 43 : $partIndex.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiGroundingPassageId(passageId=" + this.getPassageId() + ", partIndex=" + this.getPartIndex() + ")";
    }

    public static class GeminiGroundingPassageIdBuilder {
        private String passageId;
        private String partIndex;

        GeminiGroundingPassageIdBuilder() {
        }

        public GeminiGroundingPassageIdBuilder passageId(String passageId) {
            this.passageId = passageId;
            return this;
        }

        public GeminiGroundingPassageIdBuilder partIndex(String partIndex) {
            this.partIndex = partIndex;
            return this;
        }

        public GeminiGroundingPassageId build() {
            return new GeminiGroundingPassageId(this.passageId, this.partIndex);
        }

        public String toString() {
            return "GeminiGroundingPassageId.GeminiGroundingPassageIdBuilder(passageId=" + this.passageId + ", partIndex=" + this.partIndex + ")";
        }
    }
}
