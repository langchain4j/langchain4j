package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;
import java.util.Map;

/**
 * Represents the common parameters shared across guardrail checks when validating interactions
 * between a user and a language model. This class encapsulates the chat memory, user message
 * template, and additional variables required for guardrail processing.
 */
public final class GuardrailRequestParams {
    private final ChatMemory chatMemory;
    private final AugmentationResult augmentationResult;
    private final String userMessageTemplate;
    private final Map<String, Object> variables;

    private GuardrailRequestParams(Builder builder) {
        this.chatMemory = builder.chatMemory;
        this.augmentationResult = builder.augmentationResult;
        this.userMessageTemplate = ensureNotNull(builder.userMessageTemplate, "userMessageTemplate");
        this.variables = ensureNotNull(builder.variables, "variables");
    }

    /**
     * Returns the chat memory.
     *
     * @return the chat memory, may be null
     */
    public ChatMemory chatMemory() {
        return chatMemory;
    }

    /**
     * Returns the augmentation result.
     *
     * @return the augmentation result, may be null
     */
    public AugmentationResult augmentationResult() {
        return augmentationResult;
    }

    /**
     * Returns the user message template.
     *
     * @return the user message template, never null
     */
    public String userMessageTemplate() {
        return userMessageTemplate;
    }

    /**
     * Returns the variables.
     *
     * @return the variables, never null
     */
    public Map<String, Object> variables() {
        return variables;
    }

    /**
     * Creates a new builder for {@link GuardrailRequestParams}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link GuardrailRequestParams}.
     */
    public static class Builder {
        private ChatMemory chatMemory;
        private AugmentationResult augmentationResult;
        private String userMessageTemplate;
        private Map<String, Object> variables;

        /**
         * Sets the chat memory.
         *
         * @param chatMemory the chat memory
         * @return this builder
         */
        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        /**
         * Sets the augmentation result.
         *
         * @param augmentationResult the augmentation result
         * @return this builder
         */
        public Builder augmentationResult(AugmentationResult augmentationResult) {
            this.augmentationResult = augmentationResult;
            return this;
        }

        /**
         * Sets the user message template.
         *
         * @param userMessageTemplate the user message template
         * @return this builder
         */
        public Builder userMessageTemplate(String userMessageTemplate) {
            this.userMessageTemplate = userMessageTemplate;
            return this;
        }

        /**
         * Sets the variables.
         *
         * @param variables the variables
         * @return this builder
         */
        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        /**
         * Builds a new {@link GuardrailRequestParams}.
         *
         * @return a new {@link GuardrailRequestParams}
         */
        public GuardrailRequestParams build() {
            return new GuardrailRequestParams(this);
        }
    }
}
