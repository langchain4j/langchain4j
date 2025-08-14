package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
class GoogleAiEmbeddingResponse {
    GoogleAiEmbeddingResponseValues embedding;

    public GoogleAiEmbeddingResponse() {
    }

    public GoogleAiEmbeddingResponseValues getEmbedding() {
        return this.embedding;
    }

    public void setEmbedding(GoogleAiEmbeddingResponseValues embedding) {
        this.embedding = embedding;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GoogleAiEmbeddingResponse)) return false;
        final GoogleAiEmbeddingResponse other = (GoogleAiEmbeddingResponse) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$embedding = this.getEmbedding();
        final Object other$embedding = other.getEmbedding();
        if (this$embedding == null ? other$embedding != null : !this$embedding.equals(other$embedding)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GoogleAiEmbeddingResponse;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $embedding = this.getEmbedding();
        result = result * PRIME + ($embedding == null ? 43 : $embedding.hashCode());
        return result;
    }

    public String toString() {
        return "GoogleAiEmbeddingResponse(embedding=" + this.getEmbedding() + ")";
    }
}
