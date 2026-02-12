package dev.langchain4j.observation.listeners;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Experimental
public class ObservationChatModelListener implements ChatModelListener {

    public static final String OBSERVATION_SCOPE_KEY = "micrometer.observation.scope";
    private ObservationRegistry observationRegistry;

    public ObservationChatModelListener(final ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        final Observation onRequest = Observation.createNotStarted("gen_ai.client.operation.duration",
                        () -> new ChatModelObservationContext(requestContext, null, null),
                        observationRegistry)
                .lowCardinalityKeyValue("gen_ai.operation.name", "chat")
                .lowCardinalityKeyValue("gen_ai.provider.name", requestContext.modelProvider().name())
                .lowCardinalityKeyValue("gen_ai.request.model", requestContext.chatRequest().parameters().modelName())
                .contextualName("Chat request duration")
                .start();
        final Observation.Scope scope = onRequest.openScope();
        requestContext.attributes().put(OBSERVATION_SCOPE_KEY, scope);
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        Observation.Scope currentScope = (Observation.Scope) responseContext.attributes().remove(OBSERVATION_SCOPE_KEY);
        if (currentScope != null) {
            Observation observation = currentScope.getCurrentObservation();
            try {
                if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                    chatModelObservationContext.setResponseContext(responseContext);
                }
                observation.lowCardinalityKeyValue("gen_ai.response.model", responseContext.chatResponse().metadata().modelName());
            } finally {
                currentScope.close();
                observation.stop();
            }
        }
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        Observation.Scope currentScope = (Observation.Scope) errorContext.attributes().remove(OBSERVATION_SCOPE_KEY);
        if (currentScope != null) {
            Observation observation = currentScope.getCurrentObservation();
            try {
                if (observation.getContext() instanceof ChatModelObservationContext chatModelObservationContext) {
                    chatModelObservationContext.setErrorContext(errorContext);
                }
                observation.lowCardinalityKeyValue("gen_ai.response.model", responseContext.chatResponse().metadata().modelName())
                observation.error(errorContext.error());
            } finally {
                currentScope.close();
                observation.stop();
            }
        }
    }
}
