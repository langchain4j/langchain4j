package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiFileData {
    private String mimeType;
    private String fileUri;

    @JsonCreator
    GeminiFileData(@JsonProperty("mimeType") String mimeType, @JsonProperty("fileUri") String fileUri) {
        this.mimeType = mimeType;
        this.fileUri = fileUri;
    }

    public static GeminiFileDataBuilder builder() {
        return new GeminiFileDataBuilder();
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public String getFileUri() {
        return this.fileUri;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setFileUri(String fileUri) {
        this.fileUri = fileUri;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiFileData)) return false;
        final GeminiFileData other = (GeminiFileData) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$mimeType = this.getMimeType();
        final Object other$mimeType = other.getMimeType();
        if (this$mimeType == null ? other$mimeType != null : !this$mimeType.equals(other$mimeType)) return false;
        final Object this$fileUri = this.getFileUri();
        final Object other$fileUri = other.getFileUri();
        if (this$fileUri == null ? other$fileUri != null : !this$fileUri.equals(other$fileUri)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiFileData;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $mimeType = this.getMimeType();
        result = result * PRIME + ($mimeType == null ? 43 : $mimeType.hashCode());
        final Object $fileUri = this.getFileUri();
        result = result * PRIME + ($fileUri == null ? 43 : $fileUri.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiFileData(mimeType=" + this.getMimeType() + ", fileUri=" + this.getFileUri() + ")";
    }

    public static class GeminiFileDataBuilder {
        private String mimeType;
        private String fileUri;

        GeminiFileDataBuilder() {
        }

        public GeminiFileDataBuilder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public GeminiFileDataBuilder fileUri(String fileUri) {
            this.fileUri = fileUri;
            return this;
        }

        public GeminiFileData build() {
            return new GeminiFileData(this.mimeType, this.fileUri);
        }

        public String toString() {
            return "GeminiFileData.GeminiFileDataBuilder(mimeType=" + this.mimeType + ", fileUri=" + this.fileUri + ")";
        }
    }
}
