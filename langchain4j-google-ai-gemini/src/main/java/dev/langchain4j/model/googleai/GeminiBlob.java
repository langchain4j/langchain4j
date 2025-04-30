package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiBlob {
    private String mimeType;
    private String data;

    @JsonCreator
    GeminiBlob(@JsonProperty("mimeType") String mimeType, @JsonProperty("data") String data) {
        this.mimeType = mimeType;
        this.data = data;
    }

    public static GeminiBlobBuilder builder() {
        return new GeminiBlobBuilder();
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public String getData() {
        return this.data;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setData(String data) {
        this.data = data;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiBlob)) return false;
        final GeminiBlob other = (GeminiBlob) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$mimeType = this.getMimeType();
        final Object other$mimeType = other.getMimeType();
        if (this$mimeType == null ? other$mimeType != null : !this$mimeType.equals(other$mimeType)) return false;
        final Object this$data = this.getData();
        final Object other$data = other.getData();
        if (this$data == null ? other$data != null : !this$data.equals(other$data)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiBlob;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $mimeType = this.getMimeType();
        result = result * PRIME + ($mimeType == null ? 43 : $mimeType.hashCode());
        final Object $data = this.getData();
        result = result * PRIME + ($data == null ? 43 : $data.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiBlob(mimeType=" + this.getMimeType() + ", data=" + this.getData() + ")";
    }

    public static class GeminiBlobBuilder {
        private String mimeType;
        private String data;

        GeminiBlobBuilder() {
        }

        public GeminiBlobBuilder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public GeminiBlobBuilder data(String data) {
            this.data = data;
            return this;
        }

        public GeminiBlob build() {
            return new GeminiBlob(this.mimeType, this.data);
        }

        public String toString() {
            return "GeminiBlob.GeminiBlobBuilder(mimeType=" + this.mimeType + ", data=" + this.data + ")";
        }
    }
}
