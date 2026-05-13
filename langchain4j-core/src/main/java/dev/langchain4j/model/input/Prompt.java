package dev.langchain4j.model.input;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Objects;

/**
 * Represents a prompt (an input text sent to the LLM).
 * A prompt usually contains instructions, contextual information, end-user input, etc.
 * A Prompt is typically created by applying one or multiple values to a PromptTemplate.
 * <br>
 * A prompt may combine multiple {@link Content} segments (including multimodal payloads such as
 * images), or consist of a single text string.
 */
public class Prompt {

    private final List<Content> contents;

    private Prompt(List<Content> contents) {
        this.contents = copy(ensureNotEmpty(contents, "contents"));
    }

    /**
     * Create a new Prompt.
     *
     * @param text the text of the prompt.
     */
    public Prompt(String text) {
        this(List.of(TextContent.from(text)));
    }

    /**
     * Returns the modalities that make up this prompt.
     *
     * @return Unmodifiable contents list.
     */
    public List<Content> contents() {
        return contents;
    }

    /**
     * The text obtained by concatenating every {@link TextContent} segment in {@link #contents()}
     * in order.
     * <br>
     * If this prompt carries any non-text modality, this method fails with {@link IllegalStateException}
     * and {@link #contents()} must be used instead (for example {@link UserMessage UserMessages} built
     * from {@link #toUserMessage()}).
     *
     * @return contiguous text derived from fragments.
     */
    public String text() {
        StringBuilder aggregated = new StringBuilder();
        for (Content segment : contents) {
            if (segment instanceof TextContent textSegment) {
                aggregated.append(textSegment.text());
            } else {
                throw new IllegalStateException(
                        "This prompt is multimodal and cannot be reduced to plain text via text(); "
                                + "use contents() instead. First non-text segment: "
                                + segment.type());
            }
        }
        return aggregated.toString();
    }

    /**
     * Convert this prompt to a SystemMessage.
     *
     * @return the SystemMessage.
     */
    public SystemMessage toSystemMessage() {
        return systemMessage(text());
    }

    /**
     * Convert this prompt to a UserMessage with specified userName.
     *
     * @return the UserMessage.
     */
    public UserMessage toUserMessage(String userName) {
        return userMessage(userName, contents);
    }

    /**
     * Convert this prompt to a UserMessage.
     *
     * @return the UserMessage.
     */
    public UserMessage toUserMessage() {
        return userMessage(contents);
    }

    /**
     * Convert this prompt to an AiMessage.
     *
     * @return the AiMessage.
     */
    public AiMessage toAiMessage() {
        return aiMessage(text());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Prompt that = (Prompt) o;
        return Objects.equals(this.contents, that.contents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contents);
    }

    @Override
    public String toString() {
        if (contents.size() == 1 && contents.get(0) instanceof TextContent textSegment) {
            return "Prompt { text = " + quoted(textSegment.text()) + " }";
        }
        return "Prompt { contents = " + contents + " }";
    }

    /**
     * Create a text-only Prompt.
     *
     * @param text the text of the prompt.
     * @return the new Prompt.
     */
    public static Prompt from(String text) {
        return new Prompt(text);
    }

    /**
     * Create a Prompt from pre-built {@linkplain Content modalities}.
     *
     * @param contents the modality list.
     * @return the new Prompt.
     */
    public static Prompt from(List<Content> contents) {
        return new Prompt(contents);
    }
}
