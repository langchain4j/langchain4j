package dev.langchain4j.model.workersai;

/**
 * Enum for Workers AI Embedding Model Name.
 */
public enum WorkersAiEmbeddingModelName {

    // ---------------------------------------------------------------------
    // Text Embeddings
    // https://developers.cloudflare.com/workers-ai/models/text-embeddings/
    // ---------------------------------------------------------------------

    /** BAAI general embedding (bge) models transform any given text into a compact vector. */
    BAAI_EMBEDDING_SMALL("@cf/baai/bge-small-en-v1.5"),

    /** BAAI general embedding (bge) models transform any given text into a compact vector. */
    BAAI_EMBEDDING_BASE("@cf/baai/bge-base-en-v1.5"),

    /** BAAI general embedding (bge) models transform any given text into a compact vector. */
    BAAI_EMBEDDING_LARGE("@cf/baai/bge-large-en-v1.5");

    private final String stringValue;

    WorkersAiEmbeddingModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }


}
