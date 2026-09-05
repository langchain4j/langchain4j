package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer.ORIGINAL_TEXT_METADATA_KEY;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.HypotheticalQuestionTextSegmentTransformer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Restores and deduplicates original text after retrieving segments produced by
 * {@link HypotheticalQuestionTextSegmentTransformer}.
 *
 * <p>The delegate controls how candidates are searched and must return them in relevance order. Configure it to
 * retrieve more candidates than this retriever's {@code maxResults}, as multiple questions can refer to the same
 * original segment.
 *
 * <pre>{@code
 * ContentRetriever candidates = EmbeddingStoreContentRetriever.builder()
 *         .embeddingStore(embeddingStore)
 *         .embeddingModel(embeddingModel)
 *         .maxResults(9)
 *         .build();
 * ContentRetriever retriever = new HypotheticalQuestionContentRetriever(candidates, 3);
 * }</pre>
 */
public class HypotheticalQuestionContentRetriever implements ContentRetriever {

    public static final int DEFAULT_MAX_RESULTS = 3;

    private final ContentRetriever delegate;
    private final int maxResults;

    public HypotheticalQuestionContentRetriever(ContentRetriever delegate) {
        this(delegate, DEFAULT_MAX_RESULTS);
    }

    public HypotheticalQuestionContentRetriever(ContentRetriever delegate, int maxResults) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.maxResults = ensureGreaterThanZero(maxResults, "maxResults");
    }

    @Override
    public List<Content> retrieve(Query query) {
        Map<TextSegment, Content> uniqueContents = new LinkedHashMap<>();
        for (Content content : delegate.retrieve(query)) {
            TextSegment originalSegment = toOriginalSegment(content.textSegment());
            uniqueContents.putIfAbsent(originalSegment, Content.from(originalSegment, content.metadata()));
            if (uniqueContents.size() == maxResults) {
                break;
            }
        }
        return List.copyOf(uniqueContents.values());
    }

    private static TextSegment toOriginalSegment(TextSegment embeddedSegment) {
        Metadata metadata = embeddedSegment.metadata().copy();
        String originalText = ensureNotNull(
                metadata.getString(ORIGINAL_TEXT_METADATA_KEY),
                "Hypothetical question segment is missing metadata key '%s'",
                ORIGINAL_TEXT_METADATA_KEY);
        metadata.remove(ORIGINAL_TEXT_METADATA_KEY);
        return TextSegment.from(originalText, metadata);
    }
}
