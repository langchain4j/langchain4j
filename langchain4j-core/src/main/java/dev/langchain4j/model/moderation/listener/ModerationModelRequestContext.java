package dev.langchain4j.model.moderation.listener;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.ModerationRequest;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The moderation model request context.
 * It contains the {@link ModerationRequest}, {@link ModelProvider}, model name and attributes.
 * The attributes can be used to pass data between methods of a {@link ModerationModelListener}
 * or between multiple {@link ModerationModelListener}s.
 */
public class ModerationModelRequestContext {

    private final ModerationRequest moderationRequest;

    @Nullable
    private final ModelProvider modelProvider;

    @Nullable
    private final String modelName;

    private final Map<Object, Object> attributes;

    /**
     * Creates a new {@link ModerationModelRequestContext}.
     *
     * @param moderationRequest the moderation request.
     * @param modelProvider     the model provider, or {@code null} if not available.
     * @param modelName         the model name, or {@code null} if not available.
     * @param attributes        the attributes map.
     */
    public ModerationModelRequestContext(
            ModerationRequest moderationRequest,
            @Nullable ModelProvider modelProvider,
            @Nullable String modelName,
            Map<Object, Object> attributes) {
        this.moderationRequest = ensureNotNull(moderationRequest, "moderationRequest");
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.attributes = ensureNotNull(attributes, "attributes");
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
        return "ModerationModelRequestContext{" + "moderationRequest="
                + moderationRequest + ", modelProvider="
                + modelProvider + ", modelName='"
                + modelName + '\'' + ", attributes="
                + attributes + '}';
    }
}
