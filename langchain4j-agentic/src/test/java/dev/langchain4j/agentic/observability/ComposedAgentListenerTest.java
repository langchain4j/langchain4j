package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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
        assertThat(((ComposedAgentListener) composed).listeners()).containsExactly(first, second);
    }

    @Test
    void compose_with_existing_composed_listener_adds_to_it() {
        AgentListener first = new AgentListener() {};
        AgentListener second = new AgentListener() {};
        AgentListener third = new AgentListener() {};
        AgentListener composed = ComposedAgentListener.compose(first, second);

        assertThat(ComposedAgentListener.compose(composed, third)).isSameAs(composed);
        assertThat(((ComposedAgentListener) composed).listeners()).containsExactly(first, second, third);
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
    void planner_model_responses_are_forwarded_to_all_the_composed_listeners() {
        List<PlannerModelResponse> firstResponses = new ArrayList<>();
        List<PlannerModelResponse> secondResponses = new ArrayList<>();
        AgentListener composed = ComposedAgentListener.compose(
                plannerModelResponseRecorder(firstResponses), plannerModelResponseRecorder(secondResponses));
        PlannerModelResponse response = new PlannerModelResponse(null, null, null, null);

        composed.onPlannerModelResponse(response);

        assertThat(firstResponses).containsExactly(response);
        assertThat(secondResponses).containsExactly(response);
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
                },
                new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest agentRequest) {
                        requests.add(agentRequest);
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
                },
                new AgentListener() {
                    @Override
                    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
                        errors.add(agentInvocationError);
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

        assertThatThrownBy(() -> composed.onAgentSkipped(skip)).isSameAs(first).hasSuppressedException(second);
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

    @Test
    void listeners_are_notified_in_the_order_they_were_registered() {
        List<Integer> notified = new ArrayList<>();
        AgentListener composed = null;
        for (int i = 0; i <= LAST_POSITION; i++) {
            composed = ComposedAgentListener.compose(composed, orderRecorder(notified, i));
        }

        composed.beforeAgentInvocation(new AgentRequest(null, null, Map.of()));

        assertThat(notified)
                .isEqualTo(IntStream.rangeClosed(0, LAST_POSITION).boxed().toList());
    }

    @Test
    void inherited_listeners_are_notified_before_the_ones_of_the_subagent_in_the_order_they_were_registered() {
        List<Integer> notified = new ArrayList<>();
        AgentListener inherited =
                ComposedAgentListener.compose(inheritedOrderRecorder(notified, 0), inheritedOrderRecorder(notified, 1));
        AgentListener local = ComposedAgentListener.compose(orderRecorder(notified, 2), orderRecorder(notified, 3));

        ComposedAgentListener.composeWithInherited(local, inherited)
                .beforeAgentInvocation(new AgentRequest(null, null, Map.of()));

        assertThat(notified).containsExactly(0, 1, 2, 3);
    }

    @Test
    void a_listener_registered_twice_is_notified_once() {
        List<Integer> notified = new ArrayList<>();
        AgentListener listener = orderRecorder(notified, 0);
        AgentListener composed = ComposedAgentListener.compose(listener, orderRecorder(notified, 1));
        ComposedAgentListener.compose(composed, listener);

        composed.beforeAgentInvocation(new AgentRequest(null, null, Map.of()));

        assertThat(notified).containsExactly(0, 1);
    }

    /**
     * The recorders have hash codes decreasing with their position, so that a set iterating by hash code would notify
     * them in reverse.
     */
    private static final int LAST_POSITION = 19;

    private static AgentListener orderRecorder(List<Integer> notified, int position) {
        return new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                notified.add(position);
            }

            @Override
            public int hashCode() {
                return LAST_POSITION - position;
            }
        };
    }

    private static AgentListener inheritedOrderRecorder(List<Integer> notified, int position) {
        return new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                notified.add(position);
            }

            @Override
            public boolean inheritedBySubagents() {
                return true;
            }

            @Override
            public int hashCode() {
                return LAST_POSITION - position;
            }
        };
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

    private static AgentListener plannerModelResponseRecorder(List<PlannerModelResponse> responses) {
        return new AgentListener() {
            @Override
            public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
                responses.add(plannerModelResponse);
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
