package dev.langchain4j.reasoning;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.List;

/**
 * Augments chat messages with relevant {@link ReasoningStrategy} instances
 * retrieved from a {@link ReasoningBank}.
 * <p>
 * This is the entry point for integrating ReasoningBank into the LangChain4j
 * processing pipeline. It retrieves reasoning strategies that match the
 * current task and injects them into the conversation context.
 * <p>
 * Similar to RAG's {@link dev.langchain4j.rag.RetrievalAugmentor}, but instead
 * of retrieving documents, it retrieves reasoning strategies learned from
 * past experiences.
 *
 * @since 1.11.0
 */
@Experimental
public class ReasoningAugmentor {

    private final ReasoningBank reasoningBank;
    private final EmbeddingModel embeddingModel;
    private final int maxStrategies;
    private final double minScore;
    private final ReasoningInjector injector;

    private ReasoningAugmentor(Builder builder) {
        this.reasoningBank = ensureNotNull(builder.reasoningBank, "reasoningBank");
        this.embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        this.maxStrategies = getOrDefault(builder.maxStrategies, 3);
        this.minScore = getOrDefault(builder.minScore, 0.0);
        this.injector = getOrDefault(builder.injector, new DefaultReasoningInjector());
    }

    /**
     * Augments the given user message with relevant reasoning strategies.
     *
     * @param userMessage The user message to augment.
     * @return The augmentation result containing the augmented message and retrieved strategies.
     */
    public ReasoningAugmentationResult augment(UserMessage userMessage) {
        ensureNotNull(userMessage, "userMessage");

        if (reasoningBank.isEmpty()) {
            return ReasoningAugmentationResult.builder()
                    .originalMessage(userMessage)
                    .augmentedMessage(userMessage)
                    .retrievedStrategies(List.of())
                    .build();
        }

        String queryText = userMessage.singleText();
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();

        ReasoningRetrievalRequest request = ReasoningRetrievalRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxStrategies)
                .minScore(minScore)
                .build();

        ReasoningRetrievalResult retrievalResult = reasoningBank.retrieve(request);

        if (retrievalResult.isEmpty()) {
            return ReasoningAugmentationResult.builder()
                    .originalMessage(userMessage)
                    .augmentedMessage(userMessage)
                    .retrievedStrategies(List.of())
                    .build();
        }

        ChatMessage augmentedMessage = injector.inject(retrievalResult, userMessage);

        return ReasoningAugmentationResult.builder()
                .originalMessage(userMessage)
                .augmentedMessage(augmentedMessage)
                .retrievedStrategies(retrievalResult.strategies())
                .build();
    }

    /**
     * Augments a list of messages, finding and augmenting the last user message.
     *
     * @param messages The messages to process.
     * @return The augmentation result.
     */
    public ReasoningAugmentationResult augment(List<ChatMessage> messages) {
        // Find the last user message
        UserMessage lastUserMessage = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage um) {
                lastUserMessage = um;
                break;
            }
        }

        if (lastUserMessage == null) {
            throw new IllegalArgumentException("No UserMessage found in the message list");
        }

        return augment(lastUserMessage);
    }

    /**
     * Returns the reasoning bank used by this augmentor.
     *
     * @return The reasoning bank.
     */
    public ReasoningBank reasoningBank() {
        return reasoningBank;
    }

    /**
     * Returns the embedding model used by this augmentor.
     *
     * @return The embedding model.
     */
    public EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    /**
     * Creates a new builder.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ReasoningAugmentor.
     */
    public static class Builder {

        private ReasoningBank reasoningBank;
        private EmbeddingModel embeddingModel;
        private Integer maxStrategies;
        private Double minScore;
        private ReasoningInjector injector;

        public Builder reasoningBank(ReasoningBank reasoningBank) {
            this.reasoningBank = reasoningBank;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder maxStrategies(int maxStrategies) {
            this.maxStrategies = maxStrategies;
            return this;
        }

        public Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder injector(ReasoningInjector injector) {
            this.injector = injector;
            return this;
        }

        public ReasoningAugmentor build() {
            return new ReasoningAugmentor(this);
        }
    }

    /**
     * Interface for injecting reasoning strategies into messages.
     */
    public interface ReasoningInjector {

        /**
         * Injects reasoning strategies into the given message.
         *
         * @param strategies  The retrieved strategies.
         * @param userMessage The original user message.
         * @return The augmented message.
         */
        ChatMessage inject(ReasoningRetrievalResult strategies, UserMessage userMessage);
    }

    /**
     * Default implementation that prepends strategies as a system message.
     */
    public static class DefaultReasoningInjector implements ReasoningInjector {

        @Override
        public ChatMessage inject(ReasoningRetrievalResult strategies, UserMessage userMessage) {
            if (strategies.isEmpty()) {
                return userMessage;
            }

            String strategiesText = strategies.toPromptText();
            String originalText = userMessage.singleText();

            // Combine strategies with the user message
            String augmentedText =
                    strategiesText + "\nNow, apply these strategies to the following task:\n\n" + originalText;

            return UserMessage.from(augmentedText);
        }
    }
}
