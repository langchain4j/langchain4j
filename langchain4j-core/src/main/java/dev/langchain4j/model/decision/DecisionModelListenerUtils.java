package dev.langchain4j.model.decision;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
class DecisionModelListenerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionModelListenerUtils.class);

    private DecisionModelListenerUtils() {}

    static void onRequest(
            DecisionRequest request,
            ModelProvider modelProvider,
            Map<Object, Object> attributes,
            UUID invocationId,
            List<DecisionModelListener> listeners) {
        DecisionModelRequestContext context = DecisionModelRequestContext.builder()
                .decisionRequest(request)
                .modelProvider(modelProvider)
                .attributes(attributes)
                .invocationId(invocationId)
                .build();
        notify(listeners, modelProvider, listener -> listener.onRequest(context));
    }

    static void onResponse(
            DecisionResponse response,
            DecisionRequest request,
            ModelProvider modelProvider,
            Map<Object, Object> attributes,
            UUID invocationId,
            List<DecisionModelListener> listeners) {
        DecisionModelResponseContext context = DecisionModelResponseContext.builder()
                .decisionResponse(response)
                .decisionRequest(request)
                .modelProvider(modelProvider)
                .attributes(attributes)
                .invocationId(invocationId)
                .build();
        notify(listeners, modelProvider, listener -> listener.onResponse(context));
    }

    static void onError(
            Throwable error,
            DecisionRequest request,
            ModelProvider modelProvider,
            Map<Object, Object> attributes,
            UUID invocationId,
            List<DecisionModelListener> listeners) {
        DecisionModelErrorContext context = DecisionModelErrorContext.builder()
                .error(error)
                .decisionRequest(request)
                .modelProvider(modelProvider)
                .attributes(attributes)
                .invocationId(invocationId)
                .build();
        notify(listeners, modelProvider, listener -> listener.onError(context));
    }

    private static void notify(
            List<DecisionModelListener> listeners,
            ModelProvider modelProvider,
            Consumer<DecisionModelListener> notification) {
        if (isNullOrEmpty(listeners)) {
            return;
        }
        CurrentInvocation.runWithout(() -> listeners.forEach(listener -> {
            try {
                notification.accept(listener);
            } catch (Exception e) {
                LOG.warn(
                        "An exception occurred during the invocation of the decision model listener '{}'"
                                + " for model provider '{}'. This exception has been ignored.",
                        listener.getClass().getName(),
                        modelProvider,
                        e);
            }
        }));
    }
}
