package dev.langchain4j.data.segment;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNegative;

import dev.langchain4j.data.document.Metadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link TextSegmentTransformer} that enriches each {@link TextSegment} with the surrounding context
 * from neighboring segments in the current {@link #transformAll(List)} input.
 * <br>
 * This implements the <b>Sentence Window Retrieval</b> pattern for advanced RAG:
 * while embedding stores index individual (small) segments for precise retrieval,
 * the LLM receives a wider context window around each retrieved segment,
 * improving response quality.
 * <br>
 * <br>
 * The surrounding context is stored in the segment's {@link Metadata} under the key
 * {@value #SURROUNDING_CONTEXT_KEY}. It can later be extracted by
 * {@link dev.langchain4j.rag.content.injector.SentenceWindowContentInjector}.
 * <br>
 * <br>
 * <b>Note:</b> When segments contain numeric {@code index} metadata, such as segments produced by
 * standard document splitters, this transformer avoids crossing document boundaries by detecting
 * index resets. If segments do not contain usable {@code index} metadata, the input is processed as
 * a flat list and the surrounding context window may span across document boundaries.
 * <br>
 * <br>
 * Example usage:
 * <pre>{@code
 * TextSegmentTransformer transformer = SentenceWindowTextSegmentTransformer.builder()
 *         .segmentsBefore(2)
 *         .segmentsAfter(2)
 *         .build();
 *
 * EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
 *         .documentSplitter(new DocumentByParagraphSplitter(300, 0))
 *         .textSegmentTransformer(transformer)
 *         .embeddingModel(embeddingModel)
 *         .embeddingStore(embeddingStore)
 *         .build();
 * }</pre>
 *
 * @see dev.langchain4j.rag.content.injector.SentenceWindowContentInjector
 */
public class SentenceWindowTextSegmentTransformer implements TextSegmentTransformer {

    /**
     * The metadata key under which the surrounding context is stored.
     */
    public static final String SURROUNDING_CONTEXT_KEY = "surrounding_context";

    private static final String INDEX_METADATA_KEY = "index";

    private final int segmentsBefore;
    private final int segmentsAfter;

    /**
     * Creates a new {@code SentenceWindowTextSegmentTransformer}.
     *
     * @param segmentsBefore the number of preceding segments to include in the window context; must not be negative.
     * @param segmentsAfter  the number of following segments to include in the window context; must not be negative.
     */
    public SentenceWindowTextSegmentTransformer(int segmentsBefore, int segmentsAfter) {
        this.segmentsBefore = ensureNotNegative(segmentsBefore, "segmentsBefore");
        this.segmentsAfter = ensureNotNegative(segmentsAfter, "segmentsAfter");
    }

    /**
     * Transforms a single segment by enriching it with surrounding context.
     * Since a single segment has no neighbors, the surrounding context will contain
     * only the segment's own text.
     *
     * @param segment the segment to transform.
     * @return a new segment with {@value #SURROUNDING_CONTEXT_KEY} in its metadata.
     */
    @Override
    public TextSegment transform(TextSegment segment) {
        return transformAll(Collections.singletonList(segment)).get(0);
    }

    /**
     * Transforms all segments by enriching each segment's metadata
     * with the surrounding context from neighboring segments in the list.
     *
     * @param segments the list of segments to transform.
     * @return a new list of segments, each enriched with {@value #SURROUNDING_CONTEXT_KEY} in metadata.
     */
    @Override
    public List<TextSegment> transformAll(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        List<TextSegment> result = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            String surroundingContext = buildSurroundingContext(segments, i);
            Metadata newMetadata = segment.metadata().copy().put(SURROUNDING_CONTEXT_KEY, surroundingContext);
            result.add(TextSegment.from(segment.text(), newMetadata));
        }
        return result;
    }

    private String buildSurroundingContext(List<TextSegment> segments, int currentIndex) {
        int start = windowStart(segments, currentIndex);
        int end = windowEnd(segments, currentIndex);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(segments.get(i).text());
        }
        return sb.toString();
    }

    private int windowStart(List<TextSegment> segments, int currentIndex) {
        int start = Math.max(0, currentIndex - segmentsBefore);
        for (int i = currentIndex; i > start; i--) {
            if (isDocumentBoundary(segments.get(i - 1), segments.get(i))) {
                return i;
            }
        }
        return start;
    }

    private int windowEnd(List<TextSegment> segments, int currentIndex) {
        int end = Math.min(segments.size() - 1, currentIndex + segmentsAfter);
        for (int i = currentIndex; i < end; i++) {
            if (isDocumentBoundary(segments.get(i), segments.get(i + 1))) {
                return i;
            }
        }
        return end;
    }

    private static boolean isDocumentBoundary(TextSegment previous, TextSegment next) {
        Integer previousIndex = index(previous);
        Integer nextIndex = index(next);
        return previousIndex != null && nextIndex != null && nextIndex <= previousIndex;
    }

    private static Integer index(TextSegment segment) {
        Object value = segment.metadata().toMap().get(INDEX_METADATA_KEY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Creates a new {@link SentenceWindowTextSegmentTransformerBuilder}.
     *
     * @return a new builder instance.
     */
    public static SentenceWindowTextSegmentTransformerBuilder builder() {
        return new SentenceWindowTextSegmentTransformerBuilder();
    }

    public static class SentenceWindowTextSegmentTransformerBuilder {

        private int segmentsBefore = 1;
        private int segmentsAfter = 1;

        SentenceWindowTextSegmentTransformerBuilder() {}

        /**
         * Sets the number of preceding segments to include in the surrounding context.
         * Default: 1.
         *
         * @param segmentsBefore the number of preceding segments; must not be negative.
         * @return this builder.
         */
        public SentenceWindowTextSegmentTransformerBuilder segmentsBefore(int segmentsBefore) {
            this.segmentsBefore = segmentsBefore;
            return this;
        }

        /**
         * Sets the number of following segments to include in the surrounding context.
         * Default: 1.
         *
         * @param segmentsAfter the number of following segments; must not be negative.
         * @return this builder.
         */
        public SentenceWindowTextSegmentTransformerBuilder segmentsAfter(int segmentsAfter) {
            this.segmentsAfter = segmentsAfter;
            return this;
        }

        /**
         * Builds a new {@link SentenceWindowTextSegmentTransformer}.
         *
         * @return a new transformer instance.
         */
        public SentenceWindowTextSegmentTransformer build() {
            return new SentenceWindowTextSegmentTransformer(segmentsBefore, segmentsAfter);
        }
    }
}
