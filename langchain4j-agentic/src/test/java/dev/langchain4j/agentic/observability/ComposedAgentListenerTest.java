package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComposedAgentListenerTest {

    // A HashSet iterates small hash codes in ascending order. Giving the listener registered first the higher one makes
    // a HashSet iterate the listeners against their registration order, so the ordering tests below fail if the
    // registration order is lost.
    private static final int HIGHER_HASH = 1;
    private static final int LOWER_HASH = 0;

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
        // Errors are notified in reverse registration order, so the failing listener is registered last to be notified
        // first
        AgentListener composed = ComposedAgentListener.compose(
                new AgentListener() {
                    @Override
                    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
                        errors.add(agentInvocationError);
                    }
                },
                new AgentListener() {
                    @Override
                    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
                        throw failure;
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

    @Test
    void scope_opening_callbacks_and_events_notify_the_listeners_in_registration_order() {
        List<String> calls = new ArrayList<>();
        AgentListener composed = ComposedAgentListener.compose(
                callRecorder("first", calls, HIGHER_HASH), callRecorder("second", calls, LOWER_HASH));

        composed.beforeAgentInvocation(new AgentRequest(null, null, Map.of()));
        composed.beforeAgentToolExecution(new BeforeAgentToolExecution(null, null));
        composed.afterAgenticScopeCreated(null);
        composed.onAgentSkipped(new AgentSkipped(null, null, "skipped"));
        composed.onAgentAiServiceEvent(new AgentAiServiceEvent(null, null));
        composed.onPlannerModelResponse(new PlannerModelResponse(null, null, null, null));

        assertThat(calls)
                .containsExactly(
                        "first before", "second before",
                        "first beforeTool", "second beforeTool",
                        "first scopeCreated", "second scopeCreated",
                        "first skipped", "second skipped",
                        "first aiServiceEvent", "second aiServiceEvent",
                        "first plannerResponse", "second plannerResponse");
    }

    @Test
    void scope_closing_callbacks_notify_the_listeners_in_reverse_registration_order() {
        // Hash codes in registration order, so a HashSet would iterate them that way and these reversed calls would
        // fail
        List<String> calls = new ArrayList<>();
        AgentListener composed = ComposedAgentListener.compose(
                callRecorder("first", calls, LOWER_HASH), callRecorder("second", calls, HIGHER_HASH));

        composed.afterAgentInvocation(new AgentResponse(null, null, Map.of()));
        composed.onAgentInvocationError(new AgentInvocationError(null, null, Map.of(), new RuntimeException("failed")));
        composed.afterAgentToolExecution(new AfterAgentToolExecution(null, null));
        composed.beforeAgenticScopeDestroyed(null);
        composed.onAgenticSystemSuspended(null);

        assertThat(calls)
                .containsExactly(
                        "second after", "first after",
                        "second error", "first error",
                        "second afterTool", "first afterTool",
                        "second scopeDestroyed", "first scopeDestroyed",
                        "second suspended", "first suspended");
    }

    @Test
    void a_context_opened_by_a_listener_is_seen_by_the_listeners_registered_after_it_before_and_after_an_invocation() {
        ThreadLocal<String> context = new ThreadLocal<>();
        List<String> seen = new ArrayList<>();
        AgentListener contextOpener = new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                context.set("agent context");
            }

            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                context.remove();
            }

            @Override
            public int hashCode() {
                return HIGHER_HASH;
            }
        };
        AgentListener contextReader = new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                seen.add("before: " + context.get());
            }

            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                seen.add("after: " + context.get());
            }

            @Override
            public int hashCode() {
                return LOWER_HASH;
            }
        };
        AgentListener composed = ComposedAgentListener.compose(contextOpener, contextReader);

        composed.beforeAgentInvocation(new AgentRequest(null, null, Map.of()));
        composed.afterAgentInvocation(new AgentResponse(null, null, Map.of()));

        assertThat(seen).containsExactly("before: agent context", "after: agent context");
        assertThat(context.get()).isNull();
    }

    @Test
    void inherited_listeners_come_before_the_local_ones_in_registration_order() {
        List<String> calls = new ArrayList<>();
        AgentListener parent = ComposedAgentListener.compose(
                inheritedCallRecorder("parent first", calls, HIGHER_HASH),
                inheritedCallRecorder("parent second", calls, LOWER_HASH));
        AgentListener local = callRecorder("local", calls, LOWER_HASH);

        AgentListener composed = ComposedAgentListener.composeWithInherited(local, parent);
        composed.beforeAgentInvocation(new AgentRequest(null, null, Map.of()));
        composed.afterAgentInvocation(new AgentResponse(null, null, Map.of()));

        assertThat(calls)
                .containsExactly(
                        "parent first before",
                        "parent second before",
                        "local before",
                        "local after",
                        "parent second after",
                        "parent first after");
    }

    @Test
    void composing_a_composed_listener_keeps_its_order() {
        AgentListener first = callRecorder("first", new ArrayList<>(), 2);
        AgentListener second = callRecorder("second", new ArrayList<>(), 1);
        AgentListener third = callRecorder("third", new ArrayList<>(), 0);

        ComposedAgentListener composed =
                new ComposedAgentListener(ComposedAgentListener.compose(first, second), null, third);

        assertThat(composed.listeners()).containsExactly(first, second, third);
    }

    @Test
    void a_listener_registered_twice_is_notified_once_and_keeps_its_first_position() {
        List<String> calls = new ArrayList<>();
        AgentListener first = callRecorder("first", calls, LOWER_HASH);
        AgentListener second = callRecorder("second", calls, HIGHER_HASH);

        AgentListener composed = ComposedAgentListener.compose(new ComposedAgentListener(first, second, first), first);
        composed = ComposedAgentListener.compose(composed, new ComposedAgentListener(second, first));
        composed.beforeAgentInvocation(new AgentRequest(null, null, Map.of()));
        composed.afterAgentInvocation(new AgentResponse(null, null, Map.of()));

        assertThat(calls).containsExactly("first before", "second before", "second after", "first after");
    }

    private static AgentListener inheritedCallRecorder(String name, List<String> calls, int hashCode) {
        return callRecorder(name, calls, hashCode, true);
    }

    private static AgentListener callRecorder(String name, List<String> calls, int hashCode) {
        return callRecorder(name, calls, hashCode, false);
    }

    private static AgentListener callRecorder(String name, List<String> calls, int hashCode, boolean inherited) {
        return new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                calls.add(name + " before");
            }

            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                calls.add(name + " after");
            }

            @Override
            public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
                calls.add(name + " error");
            }

            @Override
            public void onAgentSkipped(AgentSkipped agentSkipped) {
                calls.add(name + " skipped");
            }

            @Override
            public void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {
                calls.add(name + " beforeTool");
            }

            @Override
            public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
                calls.add(name + " afterTool");
            }

            @Override
            public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
                calls.add(name + " aiServiceEvent");
            }

            @Override
            public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
                calls.add(name + " plannerResponse");
            }

            @Override
            public void afterAgenticScopeCreated(AgenticScope agenticScope) {
                calls.add(name + " scopeCreated");
            }

            @Override
            public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
                calls.add(name + " scopeDestroyed");
            }

            @Override
            public void onAgenticSystemSuspended(AgenticScope agenticScope) {
                calls.add(name + " suspended");
            }

            @Override
            public boolean inheritedBySubagents() {
                return inherited;
            }

            @Override
            public int hashCode() {
                return hashCode;
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
