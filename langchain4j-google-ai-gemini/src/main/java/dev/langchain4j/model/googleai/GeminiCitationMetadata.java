package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiCitationMetadata {
    private List<GeminiCitationSource> citationSources;

    @JsonCreator
    GeminiCitationMetadata(@JsonProperty("citationSources") List<GeminiCitationSource> citationSources) {
        this.citationSources = citationSources;
    }

    public static GeminiCitationMetadataBuilder builder() {
        return new GeminiCitationMetadataBuilder();
    }

    public List<GeminiCitationSource> getCitationSources() {
        return this.citationSources;
    }

    public void setCitationSources(List<GeminiCitationSource> citationSources) {
        this.citationSources = citationSources;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiCitationMetadata)) return false;
        final GeminiCitationMetadata other = (GeminiCitationMetadata) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$citationSources = this.getCitationSources();
        final Object other$citationSources = other.getCitationSources();
        if (this$citationSources == null ? other$citationSources != null : !this$citationSources.equals(other$citationSources))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiCitationMetadata;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $citationSources = this.getCitationSources();
        result = result * PRIME + ($citationSources == null ? 43 : $citationSources.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiCitationMetadata(citationSources=" + this.getCitationSources() + ")";
    }

    public static class GeminiCitationMetadataBuilder {
        private List<GeminiCitationSource> citationSources;

        GeminiCitationMetadataBuilder() {
        }

        public GeminiCitationMetadataBuilder citationSources(List<GeminiCitationSource> citationSources) {
            this.citationSources = citationSources;
            return this;
        }

        public GeminiCitationMetadata build() {
            return new GeminiCitationMetadata(this.citationSources);
        }

        public String toString() {
            return "GeminiCitationMetadata.GeminiCitationMetadataBuilder(citationSources=" + this.citationSources + ")";
        }
    }
}
