package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

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
}
