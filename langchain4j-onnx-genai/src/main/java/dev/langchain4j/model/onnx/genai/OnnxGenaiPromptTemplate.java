package dev.langchain4j.model.onnx.genai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Objects;

/**
 * Template for formatting chat messages for ONNX GenAI models.
 */
public class OnnxGenaiPromptTemplate {

    private final String systemPrefix;
    private final String userPrefix;
    private final String aiPrefix;
    private final String userSuffix;
    private final String aiSuffix;
    private final String messagesSeparator;

    /**
     * Creates a new OnnxGenaiPromptTemplate with the specified format.
     *
     * @param systemPrefix     Prefix for system messages
     * @param userPrefix       Prefix for user messages
     * @param aiPrefix         Prefix for AI messages
     * @param userSuffix       Suffix for user messages
     * @param aiSuffix         Suffix for AI messages
     * @param messagesSeparator Separator between messages
     */
    public OnnxGenaiPromptTemplate(
            String systemPrefix,
            String userPrefix,
            String aiPrefix,
            String userSuffix,
            String aiSuffix,
            String messagesSeparator) {
        this.systemPrefix = Objects.requireNonNull(systemPrefix, "systemPrefix cannot be null");
        this.userPrefix = Objects.requireNonNull(userPrefix, "userPrefix cannot be null");
        this.aiPrefix = Objects.requireNonNull(aiPrefix, "aiPrefix cannot be null");
        this.userSuffix = Objects.requireNonNull(userSuffix, "userSuffix cannot be null");
        this.aiSuffix = Objects.requireNonNull(aiSuffix, "aiSuffix cannot be null");
        this.messagesSeparator = Objects.requireNonNull(messagesSeparator, "messagesSeparator cannot be null");
    }

    /**
     * Creates a default prompt template suitable for most models.
     *
     * @return A new prompt template with default formatting
     */
    public static OnnxGenaiPromptTemplate defaultTemplate() {
        return llama32Template();
    }

    /**
     * Creates a prompt template specifically for Llama-style models.
     *
     * @return A new prompt template formatted for Llama models
     */
    public static OnnxGenaiPromptTemplate llamaTemplate() {
        return new OnnxGenaiPromptTemplate("<|system|>\n", "<|user|>\n", "<|assistant|>\n", "</s>", "</s>", "\n");
    }

    /**
     * Creates a prompt template specifically for Llama 3.2 models.
     *
     * @return A new prompt template formatted for Llama 3.2 models
     */
    public static OnnxGenaiPromptTemplate llama32Template() {
        return new OnnxGenaiPromptTemplate(
                "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n",
                "<|start_header_id|>user<|end_header_id|>\n\n",
                "<|start_header_id|>assistant<|end_header_id|>\n\n",
                "<|eot_id|>",
                "<|eot_id|>",
                "");
    }

    /**
     * Formats a list of chat messages into a single prompt string.
     *
     * @param messages The messages to format
     * @return A formatted prompt string
     */
    public String format(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        boolean needsSeparator = false;

        for (ChatMessage message : messages) {
            if (needsSeparator) {
                prompt.append(messagesSeparator);
            }

            if (message instanceof SystemMessage) {
                prompt.append(systemPrefix)
                        .append(((SystemMessage) message).text())
                        .append(userSuffix);
            } else if (message instanceof UserMessage) {
                prompt.append(userPrefix)
                        .append(((UserMessage) message).singleText())
                        .append(userSuffix);
            } else if (message instanceof AiMessage) {
                prompt.append(aiPrefix).append(((AiMessage) message).text()).append(aiSuffix);
            }

            needsSeparator = true;
        }

        // Add the AI prefix for the model's response
        if (!messages.get(messages.size() - 1).type().equals("ai")) {
            prompt.append(messagesSeparator).append(aiPrefix);
        }

        return prompt.toString();
    }

    /**
     * Builder for creating custom OnnxGenaiPromptTemplate instances.
     */
    public static class Builder {
        private String systemPrefix = "System: ";
        private String userPrefix = "User: ";
        private String aiPrefix = "Assistant: ";
        private String userSuffix = "";
        private String aiSuffix = "";
        private String messagesSeparator = "\n";

        /**
         * Sets the prefix for system messages.
         *
         * @param systemPrefix The prefix
         * @return This builder
         */
        public Builder systemPrefix(String systemPrefix) {
            this.systemPrefix = systemPrefix;
            return this;
        }

        /**
         * Sets the prefix for user messages.
         *
         * @param userPrefix The prefix
         * @return This builder
         */
        public Builder userPrefix(String userPrefix) {
            this.userPrefix = userPrefix;
            return this;
        }

        /**
         * Sets the prefix for AI messages.
         *
         * @param aiPrefix The prefix
         * @return This builder
         */
        public Builder aiPrefix(String aiPrefix) {
            this.aiPrefix = aiPrefix;
            return this;
        }

        /**
         * Sets the suffix for user messages.
         *
         * @param userSuffix The suffix
         * @return This builder
         */
        public Builder userSuffix(String userSuffix) {
            this.userSuffix = userSuffix;
            return this;
        }

        /**
         * Sets the suffix for AI messages.
         *
         * @param aiSuffix The suffix
         * @return This builder
         */
        public Builder aiSuffix(String aiSuffix) {
            this.aiSuffix = aiSuffix;
            return this;
        }

        /**
         * Sets the separator between messages.
         *
         * @param messagesSeparator The separator
         * @return This builder
         */
        public Builder messagesSeparator(String messagesSeparator) {
            this.messagesSeparator = messagesSeparator;
            return this;
        }

        /**
         * Builds a new OnnxGenaiPromptTemplate instance.
         *
         * @return A new prompt template
         */
        public OnnxGenaiPromptTemplate build() {
            return new OnnxGenaiPromptTemplate(
                    systemPrefix, userPrefix, aiPrefix, userSuffix, aiSuffix, messagesSeparator);
        }
    }
}
