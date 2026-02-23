package dev.langchain4j.model.moderation;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.moderation.listener.ModerationModelErrorContext;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelRequestContext;
import dev.langchain4j.model.moderation.listener.ModerationModelResponseContext;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
class ModerationModelListenerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ModerationModelListenerUtils.class);

    private ModerationModelListenerUtils() {}

    static void onRequest(
            ModerationRequest moderationRequest,
            ModelProvider modelProvider,
            String modelName,
            Map<Object, Object> attributes,
            List<ModerationModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        ModerationModelRequestContext requestContext =
                new ModerationModelRequestContext(moderationRequest, modelProvider, modelName, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the moderation model listener '{}'. "
                                + "This exception has been ignored.",
                        listener.getClass().getName(),
                        e);
            }
        });
    }

    static void onResponse(
            ModerationResponse moderationResponse,
            ModerationRequest moderationRequest,
            ModelProvider modelProvider,
            String modelName,
            Map<Object, Object> attributes,
            List<ModerationModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        ModerationModelResponseContext responseContext = new ModerationModelResponseContext(
                moderationResponse, moderationRequest, modelProvider, modelName, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onResponse(responseContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the moderation model listener '{}'. "
                                + "This exception has been ignored.",
                        listener.getClass().getName(),
                        e);
            }
        });
    }

    static void onError(
            Throwable error,
            ModerationRequest moderationRequest,
            ModelProvider modelProvider,
            String modelName,
            Map<Object, Object> attributes,
            List<ModerationModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        ModerationModelErrorContext errorContext =
                new ModerationModelErrorContext(error, moderationRequest, modelProvider, modelName, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onError(errorContext);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the moderation model listener '{}'. "
                                + "This exception has been ignored.",
                        listener.getClass().getName(),
                        e);
            }
        });
    }
}
