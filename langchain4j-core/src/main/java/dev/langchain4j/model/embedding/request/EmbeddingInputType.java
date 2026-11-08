package dev.langchain4j.model.embedding.request;

import dev.langchain4j.Experimental;

/**
 * The role a piece of text plays in a retrieval task, allowing a provider to encode it differently.
 * <p>
 * Embedding the same text as a {@link #QUERY} versus a {@link #DOCUMENT} can significantly improve retrieval
 * quality on providers that support the distinction (the canonical RAG "query vs passage" asymmetry). This
 * enum captures only the distinction that is universally supported across providers that expose the concept
 * (Cohere, Voyage, Nomic, Google Vertex/Gemini, NVIDIA, ...). Richer, provider-specific task types (e.g.
 * Google's {@code SEMANTIC_SIMILARITY} or {@code CODE_RETRIEVAL_QUERY}) are exposed via provider-specific
 * parameters rather than this common enum.
 *
 * @since 1.18.0
 */
@Experimental
public enum EmbeddingInputType {

    /**
     * The text is a search query (the canonical "query" side of a retrieval pair).
     */
    QUERY,

    /**
     * The text is a document/passage to be indexed and later retrieved (the "passage" side).
     */
    DOCUMENT
}
