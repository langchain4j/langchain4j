package dev.langchain4j.data.segment;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;

/**
 * A single embedding input produced by an {@link EmbeddingInputMapper}, together with the content that
 * should be stored in the embedding store for that embedding.
 * <p>
 * The two parts are usually different:
 * <ul>
 *     <li>{@link #embeddingInput()} is the text that is embedded. It can be a summary, a natural-language
 *     explanation, a hypothetical question, or the original text.</li>
 *     <li>{@link #storedSegment()} is the content stored in the embedding store and returned by retrieval.
 *     It can be the original segment or the generated description.</li>
 * </ul>
 *
 * @since 1.23.0
 */
@Experimental
public record MappedSegment(TextSegment embeddingInput, TextSegment storedSegment) {

    public MappedSegment {
        ensureNotNull(embeddingInput, "embeddingInput");
        ensureNotNull(storedSegment, "storedSegment");
    }

    /**
     * Creates a mapped segment that embeds and stores the same content.
     *
     * @param segment the segment to embed and store.
     * @return a mapped segment with the same embedding input and stored segment.
     */
    public static MappedSegment same(TextSegment segment) {
        return new MappedSegment(segment, segment);
    }

    /**
     * Creates a mapped segment with distinct embedding input and stored content.
     *
     * @param embeddingInput the text to embed.
     * @param storedSegment  the content to store in the embedding store.
     * @return a mapped segment.
     */
    public static MappedSegment of(TextSegment embeddingInput, TextSegment storedSegment) {
        return new MappedSegment(embeddingInput, storedSegment);
    }
}
