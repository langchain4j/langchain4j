package dev.langchain4j.rag.content.injector;

import static dev.langchain4j.data.segment.SentenceWindowTextSegmentTransformer.SURROUNDING_CONTEXT_KEY;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.SentenceWindowTextSegmentTransformer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import java.util.List;

/**
 * A {@link DefaultContentInjector} designed to work with {@link SentenceWindowTextSegmentTransformer}.
 * <br>
 * This implementation appends all given {@link Content}s to the end of the given {@link UserMessage}
 * in their order of iteration, using the wider surrounding context stored in the segment's
 * {@link Metadata} under the key
 * {@value dev.langchain4j.data.segment.SentenceWindowTextSegmentTransformer#SURROUNDING_CONTEXT_KEY}
 * when available, or the original segment text otherwise.
 * <br>
 * Refer to {@link #DEFAULT_PROMPT_TEMPLATE} and implementation for more details.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - promptTemplate: The prompt template that defines how the original {@code userMessage}
 * and {@code contents} are combined into the resulting {@link UserMessage}.
 * The text of the template should contain the {@code {{userMessage}}} and {@code {{contents}}} variables.
 * <br>
 * - metadataKeysToInclude: A list of {@link Metadata} keys that should be included
 * with each {@link Content#textSegment()}.
 * <br>
 * <br>
 * Example usage:
 * <pre>{@code
 * ContentInjector injector = SentenceWindowContentInjector.builder()
 *         .build();
 *
 * RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
 *         .contentInjector(injector)
 *         .build();
 * }</pre>
 *
 * @see SentenceWindowTextSegmentTransformer
 * @see DefaultContentInjector
 */
public class SentenceWindowContentInjector extends DefaultContentInjector {

    public SentenceWindowContentInjector() {
        this(DEFAULT_PROMPT_TEMPLATE, null);
    }

    /**
     * Creates a new {@code SentenceWindowContentInjector} with the given prompt template.
     *
     * @param promptTemplate the prompt template to use; if {@code null}, the {@link #DEFAULT_PROMPT_TEMPLATE} is used.
     *                       The template should contain {@code {{userMessage}}} and {@code {{contents}}} variables.
     */
    public SentenceWindowContentInjector(PromptTemplate promptTemplate) {
        this(promptTemplate, null);
    }

    /**
     * Creates a new {@code SentenceWindowContentInjector} with the given prompt template and metadata keys.
     *
     * @param promptTemplate        the prompt template to use; if {@code null}, the {@link #DEFAULT_PROMPT_TEMPLATE}
     *                              is used. The template should contain {@code {{userMessage}}} and
     *                              {@code {{contents}}} variables.
     * @param metadataKeysToInclude metadata keys to include with each {@link Content#textSegment()}.
     */
    public SentenceWindowContentInjector(PromptTemplate promptTemplate, List<String> metadataKeysToInclude) {
        super(getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE), withoutSurroundingContext(metadataKeysToInclude));
    }

    @Override
    protected String format(Content content) {
        TextSegment segment = content.textSegment();
        String surroundingContext = segment.metadata().getString(SURROUNDING_CONTEXT_KEY);
        String segmentContent = getOrDefault(surroundingContext, segment.text());
        String segmentMetadata = format(segment.metadata());
        return format(segmentContent, segmentMetadata);
    }

    private static List<String> withoutSurroundingContext(List<String> metadataKeysToInclude) {
        if (metadataKeysToInclude == null) {
            return null;
        }
        return metadataKeysToInclude.stream()
                .filter(metadataKey -> !SURROUNDING_CONTEXT_KEY.equals(metadataKey))
                .toList();
    }

    /**
     * Creates a new {@link SentenceWindowContentInjectorBuilder}.
     *
     * @return a new builder instance.
     */
    public static SentenceWindowContentInjectorBuilder builder() {
        return new SentenceWindowContentInjectorBuilder();
    }

    public static class SentenceWindowContentInjectorBuilder extends DefaultContentInjectorBuilder {

        private PromptTemplate promptTemplate;
        private List<String> metadataKeysToInclude;

        SentenceWindowContentInjectorBuilder() {}

        /**
         * Sets the prompt template to use.
         * Default: {@link #DEFAULT_PROMPT_TEMPLATE}.
         *
         * @param promptTemplate the prompt template; may be {@code null} to use the default.
         * @return this builder.
         */
        @Override
        public SentenceWindowContentInjectorBuilder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        /**
         * Sets the metadata keys to include with each {@link Content#textSegment()}.
         *
         * @param metadataKeysToInclude metadata keys to include; may be {@code null}.
         * @return this builder.
         */
        @Override
        public SentenceWindowContentInjectorBuilder metadataKeysToInclude(List<String> metadataKeysToInclude) {
            this.metadataKeysToInclude = metadataKeysToInclude;
            return this;
        }

        /**
         * Builds a new {@link SentenceWindowContentInjector}.
         *
         * @return a new injector instance.
         */
        @Override
        public SentenceWindowContentInjector build() {
            return new SentenceWindowContentInjector(promptTemplate, metadataKeysToInclude);
        }
    }
}
