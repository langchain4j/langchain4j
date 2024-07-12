package dev.langchain4j.model.input;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.Objects;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents a prompt (an input text sent to the LLM).
 * A prompt usually contains instructions, contextual information, end-user input, etc.
 * A Prompt is typically created by applying one or multiple values to a PromptTemplate.
 */
public class Prompt {

    private final String text;

    /**
     * Create a new Prompt.
     * @param text the text of the prompt.
     */
    public Prompt(String text) {
        this.text = ensureNotBlank(text, "text");
    }

    /**
     * The text of the prompt.
     * @return the text of the prompt.
     */
    public String text() {
        return text;
    }

    /**
     * Convert this prompt to a SystemMessage.
     * @return the SystemMessage.
     */
    public SystemMessage toSystemMessage() {
        return systemMessage(text);
    }

    /**
     * Convert this prompt to a UserMessage with specified userName.
     * @return the UserMessage.
     */
    public UserMessage toUserMessage(String userName) {
        return userMessage(userName, text);
    }

    /**
     * Convert this prompt to a UserMessage.
     * @return the UserMessage.
     */
    public UserMessage toUserMessage() {
        return userMessage(text);
    }

    /**
     * Convert this prompt to an AiMessage.
     * @return the AiMessage.
     */
    public AiMessage toAiMessage() {
        return aiMessage(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Prompt that = (Prompt) o;
        return Objects.equals(this.text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "Prompt {" +
                " text = " + quoted(text) +
                " }";
    }

    /**
     * Create a new Prompt.
     * @param text the text of the prompt.
     * @return the new Prompt.
     */
    public static Prompt from(String text) {
        return new Prompt(text);
    }
}
