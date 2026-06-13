package dev.langchain4j.model.embedding.onnx;

/**
 * Encodes text into embeddings and reports the token count used by the encoder.
 */
public interface Encoder {

    /**
     * Embeds the provided text.
     *
     * @param text The text to embed.
     * @return The embedding and token count.
     */
    EmbeddingAndTokenCount embed(String text);

    /**
     * Counts the tokens produced for the provided text.
     *
     * @param text The text to tokenize.
     * @return The token count.
     */
    int countTokens(String text);
}
