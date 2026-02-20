package dev.langchain4j.model.moderation;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;

import java.util.List;

/**
 * A {@link ModerationModel} which throws a {@link ModelDisabledException} for all of its methods
 * <p>
 *     This could be used in tests, or in libraries that extend this one to conditionally enable or disable functionality.
 * </p>
 */
public class DisabledModerationModel implements ModerationModel {
    @Override
    public Response<Moderation> moderate(String text) {
        throw new ModelDisabledException("ModerationModel is disabled");
    }

    @Override
    public Response<Moderation> moderate(Prompt prompt) {
        throw new ModelDisabledException("ModerationModel is disabled");
    }

    /**
     * @deprecated Use {@link #moderate(String)} instead.
     * As of 2.0.0, conversion from ChatMessage to text is the caller's responsibility.
     * See https://github.com/langchain4j/langchain4j/issues/4595
     */
    @Deprecated(forRemoval = true)
    @Override
    public Response<Moderation> moderate(ChatMessage message) {
        throw new ModelDisabledException("ModerationModel is disabled");
    }

    /**
     * @deprecated Use {@link #moderate(String)} instead.
     * As of 2.0.0, conversion from ChatMessage to text is the caller's responsibility.
     * See https://github.com/langchain4j/langchain4j/issues/4595
     */
    @Deprecated(forRemoval = true)
    @Override
    public Response<Moderation> moderate(List<ChatMessage> messages) {
        throw new ModelDisabledException("ModerationModel is disabled");
    }

    @Override
    public Response<Moderation> moderate(TextSegment textSegment) {
        throw new ModelDisabledException("ModerationModel is disabled");
    }
}
