package dev.langchain4j.model.ovhai.internal.api;

import java.util.List;

public class EmbeddingResponse {
    private List<float[]> embeddings;

    public EmbeddingResponse(List<float[]> embeddings) {
        this.embeddings = embeddings;
    }

    public EmbeddingResponse() {
    }

    public static EmbeddingResponseBuilder builder() {
        return new EmbeddingResponseBuilder();
    }

    public List<float[]> getEmbeddings() {
        return this.embeddings;
    }

    public void setEmbeddings(List<float[]> embeddings) {
        this.embeddings = embeddings;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof EmbeddingResponse)) return false;
        final EmbeddingResponse other = (EmbeddingResponse) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$embeddings = this.getEmbeddings();
        final Object other$embeddings = other.getEmbeddings();
        if (this$embeddings == null ? other$embeddings != null : !this$embeddings.equals(other$embeddings))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof EmbeddingResponse;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $embeddings = this.getEmbeddings();
        result = result * PRIME + ($embeddings == null ? 43 : $embeddings.hashCode());
        return result;
    }

    public String toString() {
        return "EmbeddingResponse(embeddings=" + this.getEmbeddings() + ")";
    }

    public static class EmbeddingResponseBuilder {
        private List<float[]> embeddings;

        EmbeddingResponseBuilder() {
        }

        public EmbeddingResponseBuilder embeddings(List<float[]> embeddings) {
            this.embeddings = embeddings;
            return this;
        }

        public EmbeddingResponse build() {
            return new EmbeddingResponse(this.embeddings);
        }

        public String toString() {
            return "EmbeddingResponse.EmbeddingResponseBuilder(embeddings=" + this.embeddings + ")";
        }
    }
}
