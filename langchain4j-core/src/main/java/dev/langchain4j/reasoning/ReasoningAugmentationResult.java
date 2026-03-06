package dev.langchain4j.reasoning;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;

/**
 * The result of a reasoning augmentation operation.
 * Contains the original message, the augmented message, and the strategies used.
 *
 * @since 1.11.0
 */
@Experimental
public class ReasoningAugmentationResult {

    private final UserMessage originalMessage;
    private final ChatMessage augmentedMessage;
    private final List<ReasoningStrategy> retrievedStrategies;

    private ReasoningAugmentationResult(Builder builder) {
        this.originalMessage = ensureNotNull(builder.originalMessage, "originalMessage");
        this.augmentedMessage = ensureNotNull(builder.augmentedMessage, "augmentedMessage");
        this.retrievedStrategies =
                builder.retrievedStrategies != null ? List.copyOf(builder.retrievedStrategies) : List.of();
    }

    /**
     * Returns the original user message before augmentation.
     *
     * @return The original message.
     */
    public UserMessage originalMessage() {
        return originalMessage;
    }

    /**
     * Returns the message after augmentation with reasoning strategies.
     *
     * @return The augmented message.
     */
    public ChatMessage augmentedMessage() {
        return augmentedMessage;
    }

    /**
     * Returns the reasoning strategies that were retrieved and used for augmentation.
     *
     * @return The list of retrieved strategies.
     */
    public List<ReasoningStrategy> retrievedStrategies() {
        return retrievedStrategies;
    }

    /**
     * Returns true if any strategies were used for augmentation.
     *
     * @return true if strategies were applied.
     */
    public boolean wasAugmented() {
        return !retrievedStrategies.isEmpty();
    }

    /**
     * Returns the number of strategies used.
     *
     * @return The count of strategies.
     */
    public int strategyCount() {
        return retrievedStrategies.size();
    }

    @Override
    public String toString() {
        return "ReasoningAugmentationResult{" + "originalMessage="
                + originalMessage + ", augmentedMessage="
                + augmentedMessage + ", retrievedStrategies="
                + retrievedStrategies.size() + '}';
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
     * Builder for ReasoningAugmentationResult.
     */
    public static class Builder {

        private UserMessage originalMessage;
        private ChatMessage augmentedMessage;
        private List<ReasoningStrategy> retrievedStrategies;

        public Builder originalMessage(UserMessage originalMessage) {
            this.originalMessage = originalMessage;
            return this;
        }

        public Builder augmentedMessage(ChatMessage augmentedMessage) {
            this.augmentedMessage = augmentedMessage;
            return this;
        }

        public Builder retrievedStrategies(List<ReasoningStrategy> retrievedStrategies) {
            this.retrievedStrategies = retrievedStrategies;
            return this;
        }

        public ReasoningAugmentationResult build() {
            return new ReasoningAugmentationResult(this);
        }
    }
}
