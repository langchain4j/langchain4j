package dev.langchain4j.agentic.observability;

import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AiServiceListenersAgentListener implements AgentListener {

    private static final Logger LOG = LoggerFactory.getLogger(AiServiceListenersAgentListener.class);

    private final boolean inheritedBySubagents;
    private final Map<Class<?>, List<AiServiceListener<AiServiceEvent>>> listenersByEventClass;

    @SuppressWarnings("unchecked")
    AiServiceListenersAgentListener(boolean inheritedBySubagents, List<AiServiceListener<?>> aiServiceListeners) {
        this.inheritedBySubagents = inheritedBySubagents;
        Map<Class<?>, List<AiServiceListener<AiServiceEvent>>> byEventClass = new HashMap<>();
        for (AiServiceListener<?> listener : aiServiceListeners) {
            if (listener.getEventClass() == AiServiceInvocationSummaryEvent.class) {
                throw new IllegalArgumentException(
                        "The AiServiceInvocationSummaryEvent is not notified to agent listeners");
            }
            byEventClass
                    .computeIfAbsent(listener.getEventClass(), eventClass -> new ArrayList<>())
                    .add((AiServiceListener<AiServiceEvent>) listener);
        }
        this.listenersByEventClass = Map.copyOf(byEventClass);
    }

    @Override
    public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
        AiServiceEvent event = agentAiServiceEvent.aiServiceEvent();
        for (AiServiceListener<AiServiceEvent> listener :
                listenersByEventClass.getOrDefault(event.eventClass(), List.of())) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.error(
                        "AI Service listener for agent " + agentAiServiceEvent.agentName() + " failed: "
                                + e.getMessage(),
                        e);
            }
        }
    }

    @Override
    public boolean inheritedBySubagents() {
        return inheritedBySubagents;
    }
}
