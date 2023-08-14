package dev.langchain4j.store.embedding.chroma;

public class ResponseValidator {
    /**
     * Validates the given SuccessfulResponse object.
     *
     * @param response The SuccessfulResponse object to validate.
     * @throws IllegalArgumentException if the validation fails.
     */
    public static void validate(SuccessfulResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Response is null.");
        }

        // Ensure that all lists (ids, distances, embeddings, documents, metadatas) have the same size
        int size = -1;
        if (response.getIds() != null && !response.getIds().isEmpty()) {
            size = response.getIds().get(0).size();
        }

        if (response.getDistances() != null && !response.getDistances().isEmpty() && response.getDistances().get(0).size() != size) {
            throw new IllegalArgumentException("Mismatched list sizes: Distances list does not match IDs list.");
        }

        if (response.getEmbeddings() != null && !response.getEmbeddings().isEmpty() && response.getEmbeddings().get(0).size() != size) {
            throw new IllegalArgumentException("Mismatched list sizes: Embeddings list does not match IDs list.");
        }

        if (response.getDocuments() != null && !response.getDocuments().isEmpty() && response.getDocuments().get(0).size() != size) {
            throw new IllegalArgumentException("Mismatched list sizes: Documents list does not match IDs list.");
        }

        if (response.getMetadatas() != null && !response.getMetadatas().isEmpty() && response.getMetadatas().get(0).size() != size) {
            throw new IllegalArgumentException("Mismatched list sizes: Metadatas list does not match IDs list.");
        }
    }
}
