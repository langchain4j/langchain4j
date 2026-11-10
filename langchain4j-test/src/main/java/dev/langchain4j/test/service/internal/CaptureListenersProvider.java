package dev.langchain4j.test.service.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.moderation.listener.ModerationModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.spi.observability.ObservabilityListenersProvider;
import java.util.List;

/**
 * Gives every AI Service and every model the listeners that pass their events to the {@link InvocationRegistry}.
 * They ignore all events while nothing is being captured.
 */
@Internal
public final class CaptureListenersProvider implements ObservabilityListenersProvider {

    private static final List<AiServiceListener<?>> AI_SERVICE_LISTENERS = List.of(
            new RecordingListener<>(AiServiceStartedEvent.class),
            new RecordingListener<>(AiServiceRequestIssuedEvent.class),
            new RecordingListener<>(AiServiceResponseReceivedEvent.class),
            new RecordingListener<>(ToolExecutedEvent.class),
            new RecordingListener<>(RetrievalAugmentedEvent.class),
            new RecordingListener<>(AiServiceCompletedEvent.class),
            new RecordingListener<>(AiServiceErrorEvent.class),
            new RecordingListener<>(AiServiceCancelledEvent.class));

    private static final ModelCallRecorder MODEL_CALL_RECORDER = new ModelCallRecorder();

    @Override
    public List<AiServiceListener<?>> aiServiceListeners() {
        InvocationRegistry.listening();
        return AI_SERVICE_LISTENERS;
    }

    @Override
    public List<ChatModelListener> chatModelListeners() {
        return List.of(MODEL_CALL_RECORDER);
    }

    @Override
    public List<EmbeddingModelListener> embeddingModelListeners() {
        return List.of(MODEL_CALL_RECORDER);
    }

    @Override
    public List<ModerationModelListener> moderationModelListeners() {
        return List.of(MODEL_CALL_RECORDER);
    }

    @Override
    public List<ScoringModelListener> scoringModelListeners() {
        return List.of(MODEL_CALL_RECORDER);
    }

    @Override
    public List<DecisionModelListener> decisionModelListeners() {
        return List.of(MODEL_CALL_RECORDER);
    }
}
