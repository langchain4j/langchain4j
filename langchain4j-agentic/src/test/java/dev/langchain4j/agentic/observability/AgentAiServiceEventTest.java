package dev.langchain4j.agentic.observability;

import static dev.langchain4j.model.chat.mock.ChatModelMock.thatAlwaysResponds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.CompensateFor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.DefaultAiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.spi.observability.AiServiceListenerRegistrarFactory;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentAiServiceEventTest {

    @Test
    void ai_service_events_are_notified_between_the_agent_invocation_callbacks() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(thatAlwaysResponds(weatherCall(), AiMessage.from("sunny")))
                .tools(new WeatherTools())
                .listener(recorder(calls, false))
                .build();

        assertThat(assistant.answer("weather")).isEqualTo("sunny");

        assertThat(calls)
                .containsExactly(
                        "before assistant",
                        "assistant AiServiceStartedEvent",
                        "assistant AiServiceRequestIssuedEvent",
                        "assistant AiServiceResponseReceivedEvent",
                        "beforeTool assistant weather",
                        "afterTool assistant weather",
                        "assistant ToolExecutedEvent",
                        "assistant AiServiceRequestIssuedEvent",
                        "assistant AiServiceResponseReceivedEvent",
                        "assistant AiServiceCompletedEvent",
                        "after assistant");
    }

    @Test
    void ai_service_events_carry_the_agent_and_the_agentic_scope_of_the_invocation() {
        List<AgentAiServiceEvent> events = new CopyOnWriteArrayList<>();
        List<AgenticScope> scopes = new CopyOnWriteArrayList<>();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(thatAlwaysResponds("hello"))
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest agentRequest) {
                        scopes.add(agentRequest.agenticScope());
                    }

                    @Override
                    public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
                        events.add(agentAiServiceEvent);
                    }
                })
                .build();

        assistant.answer("hi");

        assertThat(events).hasSize(4).allSatisfy(event -> {
            assertThat(event.agentName()).isEqualTo("assistant");
            assertThat(event.agentId()).isEqualTo("assistant");
            assertThat(event.agenticScope()).isSameAs(scopes.get(0));
            assertThat(event.aiServiceEvent().invocationContext().invocationId())
                    .isEqualTo(
                            events.get(0).aiServiceEvent().invocationContext().invocationId());
        });
    }

    @Test
    void agentic_scope_is_null_when_the_event_has_no_managed_parameters() {
        AiServiceCompletedEvent event = AiServiceCompletedEvent.builder()
                .invocationContext(InvocationContext.builder()
                        .invocationId(UUID.randomUUID())
                        .interfaceName("Assistant")
                        .methodName("answer")
                        .build())
                .build();

        assertThat(new AgentAiServiceEvent(null, event).agenticScope()).isNull();
    }

    @Test
    void inherited_listener_is_notified_of_the_ai_service_events_of_each_subagent() {
        List<String> calls = new CopyOnWriteArrayList<>();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .name("sequence")
                .subAgents(writer(thatAlwaysResponds("draft")), reviewer(thatAlwaysResponds("approved")))
                .outputKey("review")
                .listener(recorder(calls, true))
                .build();

        assertThat(sequence.invoke(Map.of("topic", "dragons"))).isEqualTo("approved");

        assertThat(calls)
                .containsExactly(
                        "before sequence",
                        "before writer",
                        "writer AiServiceStartedEvent",
                        "writer AiServiceRequestIssuedEvent",
                        "writer AiServiceResponseReceivedEvent",
                        "writer AiServiceCompletedEvent",
                        "after writer",
                        "before reviewer",
                        "reviewer AiServiceStartedEvent",
                        "reviewer AiServiceRequestIssuedEvent",
                        "reviewer AiServiceResponseReceivedEvent",
                        "reviewer AiServiceCompletedEvent",
                        "after reviewer",
                        "after sequence");
    }

    @Test
    void ai_service_events_identify_the_agent_and_its_position_in_the_agentic_system() {
        List<AgentAiServiceEvent> events = new CopyOnWriteArrayList<>();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .name("sequence")
                .subAgents(writer(thatAlwaysResponds("first")), writer(thatAlwaysResponds("second")))
                .outputKey("draft")
                .listener(eventRecorder(events, true))
                .build();

        sequence.invoke(Map.of("topic", "dragons"));

        assertThat(events).hasSize(8).allSatisfy(event -> {
            assertThat(event.agentName()).isEqualTo("writer");
            assertThat(event.agent().parent().name()).isEqualTo("sequence");
        });
        assertThat(events.get(0).agentId()).isNotEqualTo(events.get(4).agentId());
        assertThat(events.subList(0, 4))
                .extracting(AgentAiServiceEvent::agentId)
                .containsOnly(events.get(0).agentId());
    }

    @Test
    void each_agent_notifies_only_its_own_events_when_ai_services_share_a_registrar() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader sharedRegistrarLoader = sharedRegistrarClassLoader(original)) {
            Thread.currentThread().setContextClassLoader(sharedRegistrarLoader);
            UntypedAgent sequence = AgenticServices.sequenceBuilder()
                    .name("sequence")
                    .subAgents(writer(thatAlwaysResponds("draft")), reviewer(thatAlwaysResponds("approved")))
                    .outputKey("review")
                    .listener(recorder(calls, true))
                    .build();
            Chat chat = AiServices.builder(Chat.class)
                    .chatModel(thatAlwaysResponds("hello"))
                    .build();

            sequence.invoke(Map.of("topic", "dragons"));
            chat.chat("hi");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }

        assertThat(calls)
                .containsExactly(
                        "before sequence",
                        "before writer",
                        "writer AiServiceStartedEvent",
                        "writer AiServiceRequestIssuedEvent",
                        "writer AiServiceResponseReceivedEvent",
                        "writer AiServiceCompletedEvent",
                        "after writer",
                        "before reviewer",
                        "reviewer AiServiceStartedEvent",
                        "reviewer AiServiceRequestIssuedEvent",
                        "reviewer AiServiceResponseReceivedEvent",
                        "reviewer AiServiceCompletedEvent",
                        "after reviewer",
                        "after sequence");
    }

    @Test
    void ai_service_listeners_are_notified_only_of_the_events_of_their_event_class() {
        List<Object> started = new CopyOnWriteArrayList<>();
        List<Object> responses = new CopyOnWriteArrayList<>();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(writer(thatAlwaysResponds("draft")), reviewer(thatAlwaysResponds("approved")))
                .outputKey("review")
                .listener(AgentListener.forAiServiceListeners(
                        true,
                        aiServiceListener(AiServiceStartedEvent.class, started),
                        aiServiceListener(AiServiceResponseReceivedEvent.class, responses)))
                .build();

        sequence.invoke(Map.of("topic", "dragons"));

        assertThat(started).hasSize(2).allMatch(AiServiceStartedEvent.class::isInstance);
        assertThat(responses)
                .allMatch(AiServiceResponseReceivedEvent.class::isInstance)
                .extracting(event -> ((AiServiceResponseReceivedEvent) event)
                        .response()
                        .aiMessage()
                        .text())
                .containsExactly("draft", "approved");
    }

    @Test
    void ai_service_listeners_not_inherited_by_subagents_are_notified_only_of_their_own_agent_events() {
        List<AiServiceCompletedEvent> sequenceEvents = new CopyOnWriteArrayList<>();
        List<AiServiceCompletedEvent> writerEvents = new CopyOnWriteArrayList<>();
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .chatModel(thatAlwaysResponds("draft"))
                .listener(AgentListener.forAiServiceListeners(false, (AiServiceCompletedListener) writerEvents::add))
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(writer)
                .outputKey("draft")
                .listener(AgentListener.forAiServiceListeners(false, (AiServiceCompletedListener) sequenceEvents::add))
                .build();

        sequence.invoke(Map.of("topic", "dragons"));

        assertThat(sequenceEvents).isEmpty();
        assertThat(writerEvents)
                .singleElement()
                .satisfies(event -> assertThat(event.result()).contains("draft"));
    }

    @Test
    void failing_ai_service_listener_does_not_prevent_the_others_from_being_notified() {
        List<AiServiceCompletedEvent> events = new CopyOnWriteArrayList<>();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(thatAlwaysResponds("hello"))
                .listener(AgentListener.forAiServiceListeners(
                        false,
                        (AiServiceCompletedListener) event -> {
                            throw new IllegalStateException("listener failure");
                        },
                        (AiServiceCompletedListener) events::add))
                .build();

        assertThat(assistant.answer("hi")).isEqualTo("hello");

        assertThat(events).hasSize(1);
    }

    @Test
    void ai_service_listener_of_the_invocation_summary_is_rejected() {
        assertThatThrownBy(() ->
                        AgentListener.forAiServiceListeners(true, (AiServiceInvocationSummaryListener) event -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The AiServiceInvocationSummaryEvent is not notified to agent listeners");
    }

    @Test
    void listener_not_inherited_by_subagents_is_not_notified_of_their_ai_service_events() {
        List<String> calls = new CopyOnWriteArrayList<>();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .name("sequence")
                .subAgents(writer(thatAlwaysResponds("draft")))
                .outputKey("draft")
                .listener(recorder(calls, false))
                .build();

        sequence.invoke(Map.of("topic", "dragons"));

        assertThat(calls).containsExactly("before sequence", "after sequence");
    }

    @Test
    void local_and_inherited_listeners_are_both_notified_once_of_each_ai_service_event() {
        List<String> localCalls = new CopyOnWriteArrayList<>();
        List<String> inheritedCalls = new CopyOnWriteArrayList<>();
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .chatModel(thatAlwaysResponds("draft"))
                .listener(recorder(localCalls, false))
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .name("sequence")
                .subAgents(writer)
                .outputKey("draft")
                .listener(recorder(inheritedCalls, true))
                .build();

        sequence.invoke(Map.of("topic", "dragons"));

        List<String> writerCalls = List.of(
                "before writer",
                "writer AiServiceStartedEvent",
                "writer AiServiceRequestIssuedEvent",
                "writer AiServiceResponseReceivedEvent",
                "writer AiServiceCompletedEvent",
                "after writer");
        assertThat(localCalls).containsExactlyElementsOf(writerCalls);
        assertThat(inheritedCalls).containsSubsequence(writerCalls).doesNotHaveDuplicates();
    }

    @Test
    void ai_service_events_are_notified_for_an_agent_rebuilt_for_each_agentic_scope() {
        List<AgentAiServiceEvent> localEvents = new CopyOnWriteArrayList<>();
        List<AgentAiServiceEvent> inheritedEvents = new CopyOnWriteArrayList<>();
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .chatModel(thatAlwaysResponds("draft"))
                .context(agenticScope -> "Be brief.")
                .listener(eventRecorder(localEvents, false))
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .name("sequence")
                .subAgents(writer)
                .outputKey("draft")
                .listener(eventRecorder(inheritedEvents, true))
                .build();

        sequence.invoke(Map.of("topic", "dragons"));
        sequence.invoke(Map.of("topic", "unicorns"));

        assertThat(localEvents).hasSize(8).containsExactlyElementsOf(inheritedEvents);
        assertThat(localEvents).extracting(AgentAiServiceEvent::agentName).containsOnly("writer");
        assertThat(localEvents.get(0).agenticScope())
                .isNotSameAs(localEvents.get(4).agenticScope());
    }

    @Test
    void ai_service_events_of_an_async_subagent_are_notified() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .chatModel(thatAlwaysResponds("draft"))
                .async(true)
                .build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .name("sequence")
                .subAgents(writer)
                .outputKey("draft")
                .listener(recorder(calls, true))
                .build();

        assertThat(sequence.invoke(Map.of("topic", "dragons"))).isEqualTo("draft");

        assertThat(calls)
                .containsSubsequence(
                        "writer AiServiceStartedEvent",
                        "writer AiServiceRequestIssuedEvent",
                        "writer AiServiceResponseReceivedEvent",
                        "writer AiServiceCompletedEvent");
    }

    @Test
    void ai_service_events_of_a_streaming_agent_are_notified() throws Exception {
        List<String> calls = new CopyOnWriteArrayList<>();
        StreamingAssistant assistant = AgenticServices.agentBuilder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams(weatherCall(), AiMessage.from("sunny")))
                .tools(new WeatherTools())
                .listener(recorder(calls, false))
                .build();
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();

        assistant
                .answer("weather")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(response::complete)
                .onError(response::completeExceptionally)
                .start();

        assertThat(response.get(10, SECONDS).aiMessage().text()).isEqualTo("sunny");
        assertThat(calls)
                .containsSubsequence(
                        "assistant AiServiceStartedEvent",
                        "assistant AiServiceRequestIssuedEvent",
                        "assistant AiServiceResponseReceivedEvent",
                        "assistant ToolExecutedEvent",
                        "assistant AiServiceRequestIssuedEvent",
                        "assistant AiServiceResponseReceivedEvent",
                        "assistant AiServiceCompletedEvent");
    }

    @Test
    void cancellation_of_a_streaming_agent_is_notified() throws Exception {
        CompletableFuture<AgentAiServiceEvent> cancellation = new CompletableFuture<>();
        StreamingAssistant assistant = AgenticServices.agentBuilder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("a", "b", "c"))
                .listener(new AgentListener() {
                    @Override
                    public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
                        if (agentAiServiceEvent.aiServiceEvent() instanceof AiServiceCancelledEvent) {
                            cancellation.complete(agentAiServiceEvent);
                        }
                    }
                })
                .build();

        assistant
                .answer("hi")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> context.streamingHandle().cancel())
                .onError(error -> {})
                .start();

        assertThat(cancellation.get(10, SECONDS).agentName()).isEqualTo("assistant");
    }

    @Test
    void ai_service_error_is_notified_before_the_agent_invocation_error() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysThrowsExceptionWithMessage("model failure"))
                .listener(recorder(calls, false))
                .build();

        assertThatThrownBy(() -> assistant.answer("hi"))
                .isInstanceOf(AgentInvocationException.class)
                .hasRootCauseMessage("model failure");

        assertThat(calls)
                .containsExactly(
                        "before assistant",
                        "assistant AiServiceStartedEvent",
                        "assistant AiServiceRequestIssuedEvent",
                        "assistant AiServiceErrorEvent",
                        "error assistant");
    }

    @Test
    void retrieval_and_guardrail_events_are_notified() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(thatAlwaysResponds("hello"))
                .contentRetriever(query -> List.of(Content.from("source")))
                .inputGuardrails(new InputGuardrail() {
                    @Override
                    public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage userMessage) {
                        return success();
                    }
                })
                .outputGuardrails(new OutputGuardrail() {
                    @Override
                    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                        return success();
                    }
                })
                .listener(recorder(calls, false))
                .build();

        assertThat(assistant.answer("hi")).isEqualTo("hello");

        assertThat(calls)
                .containsExactly(
                        "before assistant",
                        "assistant AiServiceStartedEvent",
                        "assistant RetrievalAugmentedEvent",
                        "assistant InputGuardrailExecutedEvent",
                        "assistant AiServiceRequestIssuedEvent",
                        "assistant AiServiceResponseReceivedEvent",
                        "assistant OutputGuardrailExecutedEvent",
                        "assistant AiServiceCompletedEvent",
                        "after assistant");
    }

    @Test
    void tool_compensation_is_notified_when_the_ai_service_compensates_on_tool_errors() {
        List<String> calls = new CopyOnWriteArrayList<>();
        AgentBuilder<Assistant, ?> builder = new AgentBuilder<Assistant, AgentBuilder<Assistant, ?>>(Assistant.class) {
            @Override
            protected void build(
                    DefaultAgenticScope agenticScope, AiServiceContext context, AiServices<Assistant> aiServices) {
                aiServices.compensateOnToolErrors(true);
            }
        };
        Assistant assistant = builder.chatModel(thatAlwaysResponds(
                        AiMessage.from(toolCall("reserve-1", "reserve"), toolCall("pay-1", "pay")),
                        AiMessage.from("payment failed")))
                .tools(new BookingTools())
                .listener(recorder(calls, false))
                .build();

        assertThat(assistant.answer("book")).isEqualTo("payment failed");

        assertThat(calls).containsSubsequence("assistant ToolExecutedEvent", "assistant ToolCompensatedEvent");
    }

    @Test
    void every_event_fired_through_the_ai_service_of_the_agent_reaches_its_listener() {
        AtomicReference<AiServiceContext> context = new AtomicReference<>();
        List<AgentAiServiceEvent> events = new CopyOnWriteArrayList<>();
        assistantKeepingItsContext(context, eventRecorder(events, false));
        AiServiceCompletedEvent event = AiServiceCompletedEvent.builder()
                .invocationContext(InvocationContext.builder()
                        .invocationId(UUID.randomUUID())
                        .interfaceName("Assistant")
                        .methodName("answer")
                        .build())
                .build();

        context.get().eventListenerRegistrar.fireEvent(event);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).aiServiceEvent()).isSameAs(event);
        assertThat(events.get(0).agentName()).isEqualTo("assistant");
    }

    @Test
    void agent_listener_is_notified_also_when_the_ai_service_registrar_throws_on_listener_errors() {
        List<String> calls = new CopyOnWriteArrayList<>();
        AgentBuilder<Assistant, ?> builder = new AgentBuilder<Assistant, AgentBuilder<Assistant, ?>>(Assistant.class) {
            @Override
            protected void build(
                    DefaultAgenticScope agenticScope, AiServiceContext context, AiServices<Assistant> aiServices) {
                context.eventListenerRegistrar.shouldThrowExceptionOnEventError(true);
                context.eventListenerRegistrar.register((AiServiceStartedListener) event -> {
                    throw new IllegalStateException("listener failure");
                });
            }
        };
        Assistant assistant = builder.chatModel(thatAlwaysResponds("hello"))
                .listener(recorder(calls, false))
                .build();

        assertThatThrownBy(() -> assistant.answer("hi")).hasRootCauseMessage("listener failure");

        assertThat(calls).contains("assistant AiServiceStartedEvent");
    }

    @Test
    void listener_unregistered_through_the_ai_service_of_the_agent_is_no_longer_notified() {
        AtomicReference<AiServiceContext> context = new AtomicReference<>();
        List<AiServiceCompletedEvent> events = new CopyOnWriteArrayList<>();
        Assistant assistant = assistantKeepingItsContext(context, new AgentListener() {});
        AiServiceCompletedListener listener = events::add;

        context.get().eventListenerRegistrar.register(listener);
        context.get().eventListenerRegistrar.unregister(listener);
        assistant.answer("hi");

        assertThat(events).isEmpty();
    }

    @Test
    void error_policy_set_through_the_ai_service_of_the_agent_applies_to_its_listeners() {
        AtomicReference<AiServiceContext> context = new AtomicReference<>();
        Assistant assistant = assistantKeepingItsContext(context, new AgentListener() {});

        context.get().eventListenerRegistrar.shouldThrowExceptionOnEventError(true);
        context.get().eventListenerRegistrar.register((AiServiceStartedListener) event -> {
            throw new IllegalStateException("listener failure");
        });

        assertThatThrownBy(() -> assistant.answer("hi")).hasRootCauseMessage("listener failure");
    }

    @Test
    void failing_listener_does_not_interrupt_the_agent_invocation() {
        List<String> calls = new CopyOnWriteArrayList<>();
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(thatAlwaysResponds("hello"))
                .listener(new AgentListener() {
                    @Override
                    public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
                        calls.add(agentAiServiceEvent
                                .aiServiceEvent()
                                .eventClass()
                                .getSimpleName());
                        throw new IllegalStateException("listener failure");
                    }

                    @Override
                    public void afterAgentInvocation(AgentResponse agentResponse) {
                        calls.add("after " + agentResponse.agentName());
                    }
                })
                .build();

        assertThat(assistant.answer("hi")).isEqualTo("hello");

        assertThat(calls)
                .containsExactly(
                        "AiServiceStartedEvent",
                        "AiServiceRequestIssuedEvent",
                        "AiServiceResponseReceivedEvent",
                        "AiServiceCompletedEvent",
                        "after assistant");
    }

    @Test
    void ai_service_events_are_notified_to_a_declarative_agent_listener() {
        DeclarativeAssistant.CALLS.clear();
        DeclarativeAssistant assistant =
                AgenticServices.createAgenticSystem(DeclarativeAssistant.class, thatAlwaysResponds("hello"));

        assertThat(assistant.answer("hi")).isEqualTo("hello");

        assertThat(DeclarativeAssistant.CALLS)
                .contains(
                        "answer AiServiceStartedEvent",
                        "answer AiServiceRequestIssuedEvent",
                        "answer AiServiceResponseReceivedEvent",
                        "answer AiServiceCompletedEvent");
    }

    private static AgentListener recorder(List<String> calls, boolean inheritedBySubagents) {
        return new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                calls.add("before " + agentRequest.agentName());
            }

            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                calls.add("after " + agentResponse.agentName());
            }

            @Override
            public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
                calls.add("error " + agentInvocationError.agentName());
            }

            @Override
            public void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {
                calls.add("beforeTool "
                        + beforeAgentToolExecution.agentInstance().name() + " "
                        + beforeAgentToolExecution.toolExecution().request().name());
            }

            @Override
            public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
                calls.add("afterTool " + afterAgentToolExecution.agentInstance().name() + " "
                        + afterAgentToolExecution.toolExecution().request().name());
            }

            @Override
            public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
                calls.add(agentAiServiceEvent.agentName() + " "
                        + agentAiServiceEvent.aiServiceEvent().eventClass().getSimpleName());
            }

            @Override
            public boolean inheritedBySubagents() {
                return inheritedBySubagents;
            }
        };
    }

    private static Assistant assistantKeepingItsContext(
            AtomicReference<AiServiceContext> context, AgentListener listener) {
        AgentBuilder<Assistant, ?> builder = new AgentBuilder<Assistant, AgentBuilder<Assistant, ?>>(Assistant.class) {
            @Override
            protected void build(
                    DefaultAgenticScope agenticScope,
                    AiServiceContext aiServiceContext,
                    AiServices<Assistant> aiServices) {
                context.set(aiServiceContext);
            }
        };
        return builder.chatModel(thatAlwaysResponds("hello")).listener(listener).build();
    }

    private static AgentListener eventRecorder(List<AgentAiServiceEvent> events, boolean inheritedBySubagents) {
        return new AgentListener() {
            @Override
            public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
                events.add(agentAiServiceEvent);
            }

            @Override
            public boolean inheritedBySubagents() {
                return inheritedBySubagents;
            }
        };
    }

    private static <T extends AiServiceEvent> AiServiceListener<T> aiServiceListener(
            Class<T> eventClass, List<Object> events) {
        return new AiServiceListener<>() {
            @Override
            public Class<T> getEventClass() {
                return eventClass;
            }

            @Override
            public void onEvent(T event) {
                events.add(event);
            }
        };
    }

    private static URLClassLoader sharedRegistrarClassLoader(ClassLoader parent) throws IOException {
        String servicesFile = "META-INF/services/" + AiServiceListenerRegistrarFactory.class.getName();
        Path directory = Files.createTempDirectory("shared-registrar");
        Files.createDirectories(directory.resolve(servicesFile).getParent());
        Files.writeString(directory.resolve(servicesFile), SharedRegistrarFactory.class.getName());
        return new URLClassLoader(new URL[] {directory.toUri().toURL()}, parent) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return name.equals(servicesFile) ? findResources(name) : super.getResources(name);
            }
        };
    }

    public static class SharedRegistrarFactory implements AiServiceListenerRegistrarFactory {

        private static final AiServiceListenerRegistrar REGISTRAR = new DefaultAiServiceListenerRegistrar();

        @Override
        public AiServiceListenerRegistrar get() {
            return REGISTRAR;
        }
    }

    public interface Chat {
        String chat(String message);
    }

    private static Writer writer(ChatModelMock model) {
        return AgenticServices.agentBuilder(Writer.class).chatModel(model).build();
    }

    private static Reviewer reviewer(ChatModelMock model) {
        return AgenticServices.agentBuilder(Reviewer.class).chatModel(model).build();
    }

    private static AiMessage weatherCall() {
        return AiMessage.from(toolCall("weather-1", "weather"));
    }

    private static ToolExecutionRequest toolCall(String id, String name) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments("{}").build();
    }

    public interface Assistant {
        @UserMessage("{{request}}")
        @Agent(name = "assistant", outputKey = "answer")
        String answer(@V("request") String request);
    }

    public interface StreamingAssistant {
        @UserMessage("{{request}}")
        @Agent(name = "assistant", outputKey = "answer")
        TokenStream answer(@V("request") String request);
    }

    public interface Writer {
        @UserMessage("Write about {{topic}}")
        @Agent(name = "writer", outputKey = "draft")
        String write(@V("topic") String topic);
    }

    public interface Reviewer {
        @UserMessage("Review {{draft}}")
        @Agent(name = "reviewer", outputKey = "review")
        String review(@V("draft") String draft);
    }

    public interface DeclarativeAssistant {
        List<String> CALLS = new CopyOnWriteArrayList<>();

        @UserMessage("{{request}}")
        @Agent(outputKey = "answer")
        String answer(@V("request") String request);

        @AgentListenerSupplier
        static AgentListener listener() {
            return recorder(CALLS, false);
        }
    }

    public static class BookingTools {
        @Tool("Reserve a seat")
        String reserve() {
            return "reserved";
        }

        @CompensateFor("reserve")
        void release() {}

        @Tool("Pay for the reservation")
        String pay() {
            throw new IllegalStateException("card declined");
        }
    }

    public static class WeatherTools {
        @Tool("Get the current weather")
        String weather() {
            return "sunny";
        }
    }
}
