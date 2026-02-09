package dev.langchain4j.model.moderation.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The moderation model response context.
 * It contains {@link ModerationResponse}, corresponding {@link ModerationRequest}, {@link ModelProvider}, model name and attributes.
 * The attributes can be used to pass data between methods of a {@link ModerationModelListener}
 * or between multiple {@link ModerationModelListener}s.
 */
public class ModerationModelResponseContext {

    private final ModerationResponse moderationResponse;
    private final ModerationRequest moderationRequest;

    @Nullable
    private final ModelProvider modelProvider;

    @Nullable
    private final String modelName;

    private final Map<Object, Object> attributes;

    /**
     * Creates a new {@link ModerationModelResponseContext}.
     *
     * @param moderationResponse the moderation response.
     * @param moderationRequest  the moderation request.
     * @param modelProvider      the model provider, or {@code null} if not available.
     * @param modelName          the model name, or {@code null} if not available.
     * @param attributes         the attributes map.
     */
    public ModerationModelResponseContext(
            ModerationResponse moderationResponse,
            ModerationRequest moderationRequest,
            @Nullable ModelProvider modelProvider,
            @Nullable String modelName,
            Map<Object, Object> attributes) {
        this.moderationResponse = ensureNotNull(moderationResponse, "moderationResponse");
        this.moderationRequest = ensureNotNull(moderationRequest, "moderationRequest");
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.attributes = ensureNotNull(attributes, "attributes");
    }

    /**
     * @return The moderation response.
     */
    public ModerationResponse moderationResponse() {
        return moderationResponse;
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
     * Returns the model name.
     *
     * @return the model name, or {@code null} if not available.
     */
    @Nullable
    public String modelName() {
        return modelName;
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
        return "ModerationModelResponseContext{" + "moderationResponse="
                + moderationResponse + ", moderationRequest="
                + moderationRequest + ", modelProvider="
                + modelProvider + ", modelName='"
                + modelName + '\'' + ", attributes="
                + attributes + '}';
    }
}
