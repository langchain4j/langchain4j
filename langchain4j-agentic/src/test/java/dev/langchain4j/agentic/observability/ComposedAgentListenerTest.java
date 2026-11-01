package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComposedAgentListenerTest {

    @Test
    void compose_without_existing_listener_returns_the_new_one() {
        AgentListener listener = new AgentListener() {};

        assertThat(ComposedAgentListener.compose(null, listener)).isSameAs(listener);
    }

    @Test
    void compose_with_existing_listener_wraps_both() {
        AgentListener first = new AgentListener() {};
        AgentListener second = new AgentListener() {};

        AgentListener composed = ComposedAgentListener.compose(first, second);

        assertThat(composed).isInstanceOf(ComposedAgentListener.class);
        assertThat(((ComposedAgentListener) composed).listeners()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void compose_with_existing_composed_listener_adds_to_it() {
        AgentListener first = new AgentListener() {};
        AgentListener second = new AgentListener() {};
        AgentListener third = new AgentListener() {};
        AgentListener composed = ComposedAgentListener.compose(first, second);

        assertThat(ComposedAgentListener.compose(composed, third)).isSameAs(composed);
        assertThat(((ComposedAgentListener) composed).listeners()).containsExactlyInAnyOrder(first, second, third);
    }

    @Test
    void skips_are_forwarded_to_all_the_composed_listeners() {
        List<AgentSkipped> firstSkips = new ArrayList<>();
        List<AgentSkipped> secondSkips = new ArrayList<>();
        AgentListener composed = ComposedAgentListener.compose(skipRecorder(firstSkips), skipRecorder(secondSkips));
        AgentSkipped skip = new AgentSkipped(null, null, "activation condition is false: never");

        composed.onAgentSkipped(skip);

        assertThat(firstSkips).containsExactly(skip);
        assertThat(secondSkips).containsExactly(skip);
    }

    @Test
    void ai_service_events_are_forwarded_to_all_the_composed_listeners() {
        List<AgentAiServiceEvent> firstEvents = new ArrayList<>();
        List<AgentAiServiceEvent> secondEvents = new ArrayList<>();
        AgentListener composed = ComposedAgentListener.compose(eventRecorder(firstEvents), eventRecorder(secondEvents));
        AgentAiServiceEvent event = new AgentAiServiceEvent(null, null);

        composed.onAgentAiServiceEvent(event);

        assertThat(firstEvents).containsExactly(event);
        assertThat(secondEvents).containsExactly(event);
    }

    private static AgentListener eventRecorder(List<AgentAiServiceEvent> events) {
        return new AgentListener() {
            @Override
            public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
                events.add(agentAiServiceEvent);
            }
        };
    }

    private static AgentListener skipRecorder(List<AgentSkipped> skips) {
        return new AgentListener() {
            @Override
            public void onAgentSkipped(AgentSkipped agentSkipped) {
                skips.add(agentSkipped);
            }
        };
    }
}
