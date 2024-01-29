package dev.langchain4j.model.mistralai;

/**
 * The MistralAiEmbeddingModelName enum represents the available embedding models in the Mistral AI module.
 */
public enum MistralAiEmbeddingModelName {

    /**
     * The MISTRAL_EMBED model.
     */
    MISTRAL_EMBED("mistral-embed");

    private final String value;

    MistralAiEmbeddingModelName(String value) {
        this.value = value;
    }

    /**
     * Returns the string representation of the embedding model.
     *
     * @return the string representation of the embedding model
     */
    public String toString() {
        return this.value;
    }
}
