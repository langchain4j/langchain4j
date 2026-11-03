package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComposedAgentListenerTest {

    // The composed listeners are kept in a HashSet, which iterates small hash codes in ascending order. Giving the
    // failing listener the lower one makes it run first, so the tests below can't pass by the listener under test
    // happening to run before it.
    private static final int FIRST = 0;
    private static final int SECOND = 1;

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

    @Test
    void a_failing_listener_does_not_keep_the_others_from_being_notified_before_an_invocation() {
        List<AgentRequest> requests = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("before failed");
        AgentListener composed = ComposedAgentListener.compose(
                new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest agentRequest) {
                        throw failure;
                    }

                    @Override
                    public int hashCode() {
                        return FIRST;
                    }
                },
                new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest agentRequest) {
                        requests.add(agentRequest);
                    }

                    @Override
                    public int hashCode() {
                        return SECOND;
                    }
                });
        AgentRequest request = new AgentRequest(null, null, Map.of());

        assertThatThrownBy(() -> composed.beforeAgentInvocation(request)).isSameAs(failure);
        assertThat(requests).containsExactly(request);
    }

    @Test
    void a_failing_listener_does_not_keep_the_others_from_being_notified_of_an_error() {
        List<AgentInvocationError> errors = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("error listener failed");
        AgentListener composed = ComposedAgentListener.compose(
                new AgentListener() {
                    @Override
                    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
                        throw failure;
                    }

                    @Override
                    public int hashCode() {
                        return FIRST;
                    }
                },
                new AgentListener() {
                    @Override
                    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
                        errors.add(agentInvocationError);
                    }

                    @Override
                    public int hashCode() {
                        return SECOND;
                    }
                });
        AgentInvocationError error =
                new AgentInvocationError(null, null, Map.of(), new RuntimeException("agent failed"));

        assertThatThrownBy(() -> composed.onAgentInvocationError(error)).isSameAs(failure);
        assertThat(errors).containsExactly(error);
    }

    @Test
    void every_failure_is_reported_when_several_listeners_fail() {
        IllegalStateException first = new IllegalStateException("first");
        IllegalStateException second = new IllegalStateException("second");
        AgentListener composed = ComposedAgentListener.compose(skipFailure(first), skipFailure(second));
        AgentSkipped skip = new AgentSkipped(null, null, "activation condition is false: never");

        assertThatThrownBy(() -> composed.onAgentSkipped(skip))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(thrown -> assertThat(List.of(thrown, thrown.getSuppressed()[0]))
                        .containsExactlyInAnyOrder(first, second));
    }

    @Test
    void nothing_is_thrown_when_no_listener_fails() {
        List<AgentSkipped> firstSkips = new ArrayList<>();
        List<AgentSkipped> secondSkips = new ArrayList<>();
        AgentListener composed = ComposedAgentListener.compose(skipRecorder(firstSkips), skipRecorder(secondSkips));
        AgentSkipped skip = new AgentSkipped(null, null, "activation condition is false: never");

        assertThatCode(() -> composed.onAgentSkipped(skip)).doesNotThrowAnyException();
        assertThat(firstSkips).containsExactly(skip);
        assertThat(secondSkips).containsExactly(skip);
    }

    private static AgentListener skipFailure(RuntimeException failure) {
        return new AgentListener() {
            @Override
            public void onAgentSkipped(AgentSkipped agentSkipped) {
                throw failure;
            }
        };
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
