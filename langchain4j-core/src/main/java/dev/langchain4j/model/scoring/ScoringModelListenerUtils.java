package dev.langchain4j.model.scoring;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.scoring.listener.ScoringModelErrorContext;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
class ScoringModelListenerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ScoringModelListenerUtils.class);

    private ScoringModelListenerUtils() {}

    static void onRequest(
            ScoringRequest request,
            ModelProvider modelProvider,
            Map<Object, Object> attributes,
            UUID invocationId,
            List<ScoringModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        ScoringModelRequestContext context = ScoringModelRequestContext.builder()
                .scoringRequest(request)
                .modelProvider(modelProvider)
                .attributes(attributes)
                .invocationId(invocationId)
                .build();
        notify(listeners, modelProvider, listener -> listener.onRequest(context));
    }

    static void onResponse(
            ScoringResponse response,
            ScoringRequest request,
            ModelProvider modelProvider,
            Map<Object, Object> attributes,
            UUID invocationId,
            List<ScoringModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        ScoringModelResponseContext context = ScoringModelResponseContext.builder()
                .scoringResponse(response)
                .scoringRequest(request)
                .modelProvider(modelProvider)
                .attributes(attributes)
                .invocationId(invocationId)
                .build();
        notify(listeners, modelProvider, listener -> listener.onResponse(context));
    }

    static void onError(
            Throwable error,
            ScoringRequest request,
            ModelProvider modelProvider,
            Map<Object, Object> attributes,
            UUID invocationId,
            List<ScoringModelListener> listeners) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        ScoringModelErrorContext context = ScoringModelErrorContext.builder()
                .error(error)
                .scoringRequest(request)
                .modelProvider(modelProvider)
                .attributes(attributes)
                .invocationId(invocationId)
                .build();
        notify(listeners, modelProvider, listener -> listener.onError(context));
    }

    private static void notify(
            List<ScoringModelListener> listeners,
            ModelProvider modelProvider,
            Consumer<ScoringModelListener> notification) {
        CurrentInvocation.runWithout(() -> listeners.forEach(listener -> {
            try {
                notification.accept(listener);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the scoring model listener '{}'"
                                + " for model provider '{}'. This exception has been ignored.",
                        listener.getClass().getName(),
                        modelProvider,
                        e);
            }
        }));
    }
}
