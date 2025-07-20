package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class GoogleAiBatchEmbeddingResponse {
    List<GoogleAiEmbeddingResponseValues> embeddings;

    public GoogleAiBatchEmbeddingResponse() {
    }

    public List<GoogleAiEmbeddingResponseValues> getEmbeddings() {
        return this.embeddings;
    }

    public void setEmbeddings(List<GoogleAiEmbeddingResponseValues> embeddings) {
        this.embeddings = embeddings;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GoogleAiBatchEmbeddingResponse)) return false;
        final GoogleAiBatchEmbeddingResponse other = (GoogleAiBatchEmbeddingResponse) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$embeddings = this.getEmbeddings();
        final Object other$embeddings = other.getEmbeddings();
        if (this$embeddings == null ? other$embeddings != null : !this$embeddings.equals(other$embeddings))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GoogleAiBatchEmbeddingResponse;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $embeddings = this.getEmbeddings();
        result = result * PRIME + ($embeddings == null ? 43 : $embeddings.hashCode());
        return result;
    }

    public String toString() {
        return "GoogleAiBatchEmbeddingResponse(embeddings=" + this.getEmbeddings() + ")";
    }
}
