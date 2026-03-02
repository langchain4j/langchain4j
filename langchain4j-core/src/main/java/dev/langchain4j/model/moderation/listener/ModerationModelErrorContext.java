package dev.langchain4j.model.moderation.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.ModerationRequest;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The moderation model error context.
 * It contains the error, corresponding {@link ModerationRequest}, {@link ModelProvider} and attributes.
 * The attributes can be used to pass data between methods of a {@link ModerationModelListener}
 * or between multiple {@link ModerationModelListener}s.
 */
public class ModerationModelErrorContext {

    private final Throwable error;
    private final ModerationRequest moderationRequest;

    @Nullable
    private final ModelProvider modelProvider;

    private final Map<Object, Object> attributes;

    /**
     * Creates a new {@link ModerationModelErrorContext}.
     *
     * @param error              the error that occurred.
     * @param moderationRequest  the moderation request.
     * @param modelProvider      the model provider, or {@code null} if not available.
     * @param attributes         the attributes map.
     */
    public ModerationModelErrorContext(
            Throwable error,
            ModerationRequest moderationRequest,
            @Nullable ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        this.error = ensureNotNull(error, "error");
        this.moderationRequest = ensureNotNull(moderationRequest, "moderationRequest");
        this.modelProvider = modelProvider;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @return The error that occurred.
     */
    public Throwable error() {
        return error;
    }

    /**
     * @return The moderation request.
     */
    public ModerationRequest moderationRequest() {
        return moderationRequest;
    }

    /**
     * @return The model provider, or {@code null} if not available.
     */
    @Nullable
    public ModelProvider modelProvider() {
        return modelProvider;
    }

    /**
     * @return The attributes map. It can be used to pass data between methods of a {@link ModerationModelListener}
     * or between multiple {@link ModerationModelListener}s.
     */
    public Map<Object, Object> attributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "ModerationModelErrorContext{" + "error="
                + error + ", moderationRequest="
                + moderationRequest + ", modelProvider="
                + modelProvider + ", attributes="
                + attributes + '}';
    }
}
