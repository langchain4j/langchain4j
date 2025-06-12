package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiCitationSource {
    private Integer startIndex;
    private Integer endIndex;
    private String uri;
    private String license;

    @JsonCreator
    GeminiCitationSource(@JsonProperty("startIndex") Integer startIndex,
                         @JsonProperty("endIndex") Integer endIndex,
                         @JsonProperty("uri") String uri,
                         @JsonProperty("license") String license) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.uri = uri;
        this.license = license;
    }

    public static GeminiCitationSourceBuilder builder() {
        return new GeminiCitationSourceBuilder();
    }

    public Integer getStartIndex() {
        return this.startIndex;
    }

    public Integer getEndIndex() {
        return this.endIndex;
    }

    public String getUri() {
        return this.uri;
    }

    public String getLicense() {
        return this.license;
    }

    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    public void setEndIndex(Integer endIndex) {
        this.endIndex = endIndex;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiCitationSource)) return false;
        final GeminiCitationSource other = (GeminiCitationSource) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$startIndex = this.getStartIndex();
        final Object other$startIndex = other.getStartIndex();
        if (this$startIndex == null ? other$startIndex != null : !this$startIndex.equals(other$startIndex))
            return false;
        final Object this$endIndex = this.getEndIndex();
        final Object other$endIndex = other.getEndIndex();
        if (this$endIndex == null ? other$endIndex != null : !this$endIndex.equals(other$endIndex)) return false;
        final Object this$uri = this.getUri();
        final Object other$uri = other.getUri();
        if (this$uri == null ? other$uri != null : !this$uri.equals(other$uri)) return false;
        final Object this$license = this.getLicense();
        final Object other$license = other.getLicense();
        if (this$license == null ? other$license != null : !this$license.equals(other$license)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiCitationSource;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $startIndex = this.getStartIndex();
        result = result * PRIME + ($startIndex == null ? 43 : $startIndex.hashCode());
        final Object $endIndex = this.getEndIndex();
        result = result * PRIME + ($endIndex == null ? 43 : $endIndex.hashCode());
        final Object $uri = this.getUri();
        result = result * PRIME + ($uri == null ? 43 : $uri.hashCode());
        final Object $license = this.getLicense();
        result = result * PRIME + ($license == null ? 43 : $license.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiCitationSource(startIndex=" + this.getStartIndex() + ", endIndex=" + this.getEndIndex() + ", uri=" + this.getUri() + ", license=" + this.getLicense() + ")";
    }

    public static class GeminiCitationSourceBuilder {
        private Integer startIndex;
        private Integer endIndex;
        private String uri;
        private String license;

        GeminiCitationSourceBuilder() {
        }

        public GeminiCitationSourceBuilder startIndex(Integer startIndex) {
            this.startIndex = startIndex;
            return this;
        }

        public GeminiCitationSourceBuilder endIndex(Integer endIndex) {
            this.endIndex = endIndex;
            return this;
        }

        public GeminiCitationSourceBuilder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public GeminiCitationSourceBuilder license(String license) {
            this.license = license;
            return this;
        }

        public GeminiCitationSource build() {
            return new GeminiCitationSource(this.startIndex, this.endIndex, this.uri, this.license);
        }

        public String toString() {
            return "GeminiCitationSource.GeminiCitationSourceBuilder(startIndex=" + this.startIndex + ", endIndex=" + this.endIndex + ", uri=" + this.uri + ", license=" + this.license + ")";
        }
    }
}
