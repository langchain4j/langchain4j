package dev.langchain4j.model.mistralai;

/**
 * The EmbeddingModel enum represents the available embedding models in the Mistral AI module.
 */
enum EmbeddingModel {

    /**
     * The MISTRAL_EMBED model.
     */
    MISTRAL_EMBED("mistral-embed");

    private final String value;

    private EmbeddingModel(String value) {
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
