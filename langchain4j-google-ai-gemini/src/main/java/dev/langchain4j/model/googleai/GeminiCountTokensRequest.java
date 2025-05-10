package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiCountTokensRequest {
    List<GeminiContent> contents;
    GeminiGenerateContentRequest generateContentRequest;

    public GeminiCountTokensRequest() {
    }

    public List<GeminiContent> getContents() {
        return this.contents;
    }

    public GeminiGenerateContentRequest getGenerateContentRequest() {
        return this.generateContentRequest;
    }

    public void setContents(List<GeminiContent> contents) {
        this.contents = contents;
    }

    public void setGenerateContentRequest(GeminiGenerateContentRequest generateContentRequest) {
        this.generateContentRequest = generateContentRequest;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiCountTokensRequest)) return false;
        final GeminiCountTokensRequest other = (GeminiCountTokensRequest) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$contents = this.getContents();
        final Object other$contents = other.getContents();
        if (this$contents == null ? other$contents != null : !this$contents.equals(other$contents)) return false;
        final Object this$generateContentRequest = this.getGenerateContentRequest();
        final Object other$generateContentRequest = other.getGenerateContentRequest();
        if (this$generateContentRequest == null ? other$generateContentRequest != null : !this$generateContentRequest.equals(other$generateContentRequest))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiCountTokensRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $contents = this.getContents();
        result = result * PRIME + ($contents == null ? 43 : $contents.hashCode());
        final Object $generateContentRequest = this.getGenerateContentRequest();
        result = result * PRIME + ($generateContentRequest == null ? 43 : $generateContentRequest.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiCountTokensRequest(contents=" + this.getContents() + ", generateContentRequest=" + this.getGenerateContentRequest() + ")";
    }
}
