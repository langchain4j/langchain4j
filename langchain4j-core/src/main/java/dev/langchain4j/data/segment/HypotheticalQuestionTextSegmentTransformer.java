package dev.langchain4j.data.segment;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates hypothetical questions for each {@link TextSegment} during ingestion.
 * The original text is stored in each generated segment's metadata so that it can be restored by
 * {@link dev.langchain4j.rag.content.retriever.HypotheticalQuestionContentRetriever}.
 *
 * <p>This is a one-to-many transformation. Use {@link #transformAll(List)} rather than
 * {@link #transform(TextSegment)}.
 */
public class HypotheticalQuestionTextSegmentTransformer implements TextSegmentTransformer {

    public static final String ORIGINAL_TEXT_METADATA_KEY = "hqe_original_text";
    public static final int DEFAULT_NUMBER_OF_QUESTIONS = 3;
    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from("""
                    Generate {{numberOfQuestions}} hypothetical questions that the following text could answer. \
                    Each question should be a complete, standalone question. \
                    It is very important to provide each question on a separate line, \
                    without enumerations, hyphens, or any additional formatting!
                    Text: {{text}}""");

    private final ChatModel chatModel;
    private final PromptTemplate promptTemplate;
    private final int numberOfQuestions;

    public HypotheticalQuestionTextSegmentTransformer(ChatModel chatModel) {
        this(chatModel, DEFAULT_PROMPT_TEMPLATE, DEFAULT_NUMBER_OF_QUESTIONS);
    }

    public HypotheticalQuestionTextSegmentTransformer(ChatModel chatModel, int numberOfQuestions) {
        this(chatModel, DEFAULT_PROMPT_TEMPLATE, numberOfQuestions);
    }

    public HypotheticalQuestionTextSegmentTransformer(ChatModel chatModel, PromptTemplate promptTemplate) {
        this(chatModel, ensureNotNull(promptTemplate, "promptTemplate"), DEFAULT_NUMBER_OF_QUESTIONS);
    }

    public HypotheticalQuestionTextSegmentTransformer(
            ChatModel chatModel, PromptTemplate promptTemplate, Integer numberOfQuestions) {
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.numberOfQuestions = ensureGreaterThanZero(
                getOrDefault(numberOfQuestions, DEFAULT_NUMBER_OF_QUESTIONS), "numberOfQuestions");
    }

    @Override
    public TextSegment transform(TextSegment segment) {
        throw new UnsupportedOperationException("Use transformAll() for this one-to-many transformation");
    }

    @Override
    public List<TextSegment> transformAll(List<TextSegment> segments) {
        ensureNotNull(segments, "segments").forEach(this::validateMetadata);

        List<TextSegment> result = new ArrayList<>();
        for (TextSegment segment : segments) {
            List<String> questions = generateQuestions(segment);
            if (questions.isEmpty()) {
                questions = List.of(segment.text());
            }
            for (String question : questions) {
                result.add(toQuestionSegment(question, segment));
            }
        }
        return result;
    }

    private List<String> generateQuestions(TextSegment segment) {
        String response = chatModel.chat(promptTemplate
                .apply(Map.of("text", segment.text(), "numberOfQuestions", numberOfQuestions))
                .text());
        return response.lines()
                .map(String::trim)
                .filter(question -> isNotNullOrBlank(question))
                .limit(numberOfQuestions)
                .toList();
    }

    private void validateMetadata(TextSegment segment) {
        if (segment.metadata().containsKey(ORIGINAL_TEXT_METADATA_KEY)) {
            throw illegalArgument("Metadata key '%s' is reserved", ORIGINAL_TEXT_METADATA_KEY);
        }
    }

    private static TextSegment toQuestionSegment(String question, TextSegment originalSegment) {
        Metadata metadata = originalSegment.metadata().copy().put(ORIGINAL_TEXT_METADATA_KEY, originalSegment.text());
        return TextSegment.from(question, metadata);
    }

    public static HypotheticalQuestionTextSegmentTransformerBuilder builder() {
        return new HypotheticalQuestionTextSegmentTransformerBuilder();
    }

    public static class HypotheticalQuestionTextSegmentTransformerBuilder {

        private ChatModel chatModel;
        private PromptTemplate promptTemplate;
        private Integer numberOfQuestions;

        HypotheticalQuestionTextSegmentTransformerBuilder() {}

        public HypotheticalQuestionTextSegmentTransformerBuilder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public HypotheticalQuestionTextSegmentTransformerBuilder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public HypotheticalQuestionTextSegmentTransformerBuilder numberOfQuestions(Integer numberOfQuestions) {
            this.numberOfQuestions = numberOfQuestions;
            return this;
        }

        public HypotheticalQuestionTextSegmentTransformer build() {
            return new HypotheticalQuestionTextSegmentTransformer(chatModel, promptTemplate, numberOfQuestions);
        }
    }
}
