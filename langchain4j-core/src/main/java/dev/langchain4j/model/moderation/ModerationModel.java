package dev.langchain4j.model.moderation;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * Represents a model that can moderate text.
 */
public interface ModerationModel {

    /**
     * Moderates the given text.
     * @param text the text to moderate.
     * @return the moderation {@code Response}.
     */
    Response<Moderation> moderate(String text);

    /**
     * Moderates a list of texts.
     * @param texts the texts to moderate.
     * @return the moderation {@code Response}.
     */
    Response<Moderation> moderate(List<String> texts);

    /**
     * Moderates the given prompt.
     * @param prompt the prompt to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(Prompt prompt) {
        return moderate(prompt.text());
    }

    /**
     * Moderates the given text segment.
     * @param textSegment the text segment to moderate.
     * @return the moderation {@code Response}.
     */
    default Response<Moderation> moderate(TextSegment textSegment) {
        return moderate(textSegment.text());
    }
}
