package dev.langchain4j.data.segment;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TextSegmentTransformer} that generates hypothetical questions for each {@link TextSegment}
 * using a {@link ChatModel}, implementing the <b>Hypothetical Question Embedding (HQE)</b> pattern
 * for advanced RAG.
 * <br>
 * <br>
 * During ingestion, for each text segment, this transformer uses a {@link ChatModel} to generate
 * {@code N} hypothetical questions that the segment could answer. Each question becomes a new
 * {@link TextSegment}, with the original text stored in the segment's {@link Metadata} under the key
 * {@value #ORIGINAL_TEXT_METADATA_KEY}.
 * <br>
 * <br>
 * The embedding store then indexes these question segments instead of the original text.
 * At retrieval time, user queries match against the hypothetical questions (which are often closer
 * in semantic space to how users phrase queries), improving retrieval quality.
 * If no valid question is generated for a segment, the original segment text is used as the indexed
 * text for that segment.
 * <br>
 * <br>
 * <b>Note:</b> This is a 1:N transformation. Each input segment produces multiple output segments.
 * The {@link #transform(TextSegment)} method is not supported; use {@link #transformAll(List)} instead.
 * <br>
 * <br>
 * <b>Note:</b> This transformer calls the {@link ChatModel} once per input segment, which can be slow
 * for large numbers of segments. Consider the ingestion cost when choosing the number of questions.
 * <br>
 * <br>
 * Example usage:
 * <pre>{@code
 * TextSegmentTransformer transformer = HypotheticalQuestionTextSegmentTransformer.builder()
 *         .chatModel(chatModel)
 *         .numberOfQuestions(3)
 *         .build();
 *
 * EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
 *         .documentSplitter(DocumentSplitters.recursive(300, 0))
 *         .textSegmentTransformer(transformer)
 *         .embeddingModel(embeddingModel)
 *         .embeddingStore(embeddingStore)
 *         .build();
 * }</pre>
 *
 * @see dev.langchain4j.rag.content.retriever.HypotheticalQuestionContentRetriever
 */
public class HypotheticalQuestionTextSegmentTransformer implements TextSegmentTransformer {

    private static final Logger log = LoggerFactory.getLogger(HypotheticalQuestionTextSegmentTransformer.class);

    /**
     * The metadata key under which the original text of the segment is stored.
     */
    public static final String ORIGINAL_TEXT_METADATA_KEY = "hqe_original_text";

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from("""
        Generate {{numberOfQuestions}} hypothetical questions that the following text could answer. \
        Each question should be a complete, standalone question. \
        It is very important to provide each question on a separate line, \
        without enumerations, hyphens, or any additional formatting!
        Text: {{text}}\
        """);

    public static final int DEFAULT_NUMBER_OF_QUESTIONS = 3;
    private static final Pattern QUESTION_LIST_PREFIX_PATTERN = Pattern.compile("^(?:[-*]\\s+|\\d+[.)]\\s+)");

    private final ChatModel chatModel;
    private final PromptTemplate promptTemplate;
    private final int numberOfQuestions;

    /**
     * Creates a new {@code HypotheticalQuestionTextSegmentTransformer}.
     *
     * @param chatModel         the {@link ChatModel} to use for generating hypothetical questions. Mandatory.
     * @param promptTemplate    the {@link PromptTemplate} to use. Optional, defaults to {@link #DEFAULT_PROMPT_TEMPLATE}.
     * @param numberOfQuestions the number of questions to generate per segment. Optional, defaults to {@value #DEFAULT_NUMBER_OF_QUESTIONS}.
     */
    public HypotheticalQuestionTextSegmentTransformer(
            ChatModel chatModel, PromptTemplate promptTemplate, Integer numberOfQuestions) {
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.numberOfQuestions = ensureGreaterThanZero(
                getOrDefault(numberOfQuestions, DEFAULT_NUMBER_OF_QUESTIONS), "numberOfQuestions");
    }

    /**
     * Not supported. HQE is a 1:N transformation; use {@link #transformAll(List)} instead.
     *
     * @param segment ignored.
     * @return never returns normally.
     * @throws UnsupportedOperationException always.
     */
    @Override
    public TextSegment transform(TextSegment segment) {
        throw new UnsupportedOperationException("HQE is a 1:N transformation. Use transformAll() instead.");
    }

    /**
     * Transforms all segments by generating hypothetical questions for each segment.
     * Each input segment produces multiple output segments, where each output segment's text
     * is a hypothetical question and its metadata contains the original text under
     * {@value #ORIGINAL_TEXT_METADATA_KEY}.
     *
     * @param segments the list of segments to transform.
     * @return a list of question segments. The list is typically longer than the input list.
     */
    @Override
    public List<TextSegment> transformAll(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        List<TextSegment> result = new ArrayList<>();
        for (TextSegment segment : segments) {
            List<String> questions = generateQuestions(segment);
            if (questions.isEmpty()) {
                log.warn(
                        "No hypothetical questions were generated for a segment ({} characters). "
                                + "The original segment will be used instead.",
                        segment.text().length());
                result.add(toQuestionSegment(segment.text(), segment));
                continue;
            }
            for (String question : questions) {
                result.add(toQuestionSegment(question, segment));
            }
        }
        return result;
    }

    private static TextSegment toQuestionSegment(String question, TextSegment segment) {
        if (segment.metadata().containsKey(ORIGINAL_TEXT_METADATA_KEY)) {
            throw illegalArgument("Metadata key '%s' is reserved for HQE", ORIGINAL_TEXT_METADATA_KEY);
        }
        Metadata newMetadata = segment.metadata().copy().put(ORIGINAL_TEXT_METADATA_KEY, segment.text());
        return TextSegment.from(question, newMetadata);
    }

    private List<String> generateQuestions(TextSegment segment) {
        Prompt prompt = promptTemplate.apply(Map.of("text", segment.text(), "numberOfQuestions", numberOfQuestions));

        String response = chatModel.chat(prompt.text());
        return parse(response);
    }

    private List<String> parse(String response) {
        List<String> questions = new ArrayList<>();
        if (response == null) {
            return questions;
        }
        int discardedQuestions = 0;
        for (String line : response.split("\\R")) {
            String question = normalizeQuestion(line);
            if (question == null) {
                continue;
            }
            if (questions.size() < numberOfQuestions) {
                questions.add(question);
            } else {
                discardedQuestions++;
            }
        }
        if (discardedQuestions > 0) {
            log.warn(
                    "Chat model generated {} extra hypothetical questions; only the first {} will be used.",
                    discardedQuestions,
                    numberOfQuestions);
        }
        return questions;
    }

    private String normalizeQuestion(String line) {
        if (!isNotNullOrBlank(line)) {
            return null;
        }
        String normalized = QUESTION_LIST_PREFIX_PATTERN
                .matcher(line.trim())
                .replaceFirst("")
                .trim();
        return isNotNullOrBlank(normalized) ? normalized : null;
    }

    /**
     * Creates a new {@link HypotheticalQuestionTextSegmentTransformerBuilder}.
     *
     * @return a new builder instance.
     */
    public static HypotheticalQuestionTextSegmentTransformerBuilder builder() {
        return new HypotheticalQuestionTextSegmentTransformerBuilder();
    }

    public static class HypotheticalQuestionTextSegmentTransformerBuilder {

        private ChatModel chatModel;
        private PromptTemplate promptTemplate;
        private Integer numberOfQuestions;

        HypotheticalQuestionTextSegmentTransformerBuilder() {}

        /**
         * Sets the {@link ChatModel} to use for generating hypothetical questions. Mandatory.
         *
         * @param chatModel the chat model.
         * @return this builder.
         */
        public HypotheticalQuestionTextSegmentTransformerBuilder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        /**
         * Sets the {@link PromptTemplate} to use.
         * Default: {@link #DEFAULT_PROMPT_TEMPLATE}.
         * The template should contain {@code {{text}}} and {@code {{numberOfQuestions}}} variables.
         *
         * @param promptTemplate the prompt template.
         * @return this builder.
         */
        public HypotheticalQuestionTextSegmentTransformerBuilder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        /**
         * Sets the number of hypothetical questions to generate per segment.
         * Default: {@value #DEFAULT_NUMBER_OF_QUESTIONS}.
         *
         * @param numberOfQuestions the number of questions; must be greater than zero.
         * @return this builder.
         */
        public HypotheticalQuestionTextSegmentTransformerBuilder numberOfQuestions(Integer numberOfQuestions) {
            if (numberOfQuestions != null) {
                this.numberOfQuestions = ensureGreaterThanZero(numberOfQuestions, "numberOfQuestions");
            }
            return this;
        }

        /**
         * Builds a new {@link HypotheticalQuestionTextSegmentTransformer}.
         *
         * @return a new transformer instance.
         */
        public HypotheticalQuestionTextSegmentTransformer build() {
            return new HypotheticalQuestionTextSegmentTransformer(chatModel, promptTemplate, numberOfQuestions);
        }
    }
}
