package dev.langchain4j.service.guardrail;

import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.MEMORY_ID;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.REWRITTEN_ARGUMENTS;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.TEMPLATE;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.TIMEOUT;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.TOPIC;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.input;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.output;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.run;
import static dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.searchCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.SingletonClassInstanceFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.BlockingToolInputGuardrail;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.Mode;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.SearchTool;
import dev.langchain4j.service.guardrail.ToolGuardrailTestSupport.ToolKind;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.observability.api.listener.InputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.OutputGuardrailExecutedListener;

/**
 * What a tool guardrail knows about the invocation it runs in, where the tool came from, and where its events
 * go.
 */
class ToolGuardrailContextTests extends BaseGuardrailTests {

    // ---------- request parameters ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_input_request_carries_the_chat_memory_the_memory_id_the_template_and_the_variables_of_the_invocation(
            Mode mode) {
        var requests = new CopyOnWriteArrayList<ToolInputGuardrailRequest>();

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, SearchTool.echoing());
            services.toolInputGuardrails(input(request -> {
                requests.add(request);
                return ToolInputGuardrailResult.success();
            }));
        });

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(requests).singleElement().satisfies(request -> {
            assertParams(request.requestParams(), run);
            assertThat(request.memoryId()).isEqualTo(MEMORY_ID);
            assertThat(request.invocationContext().chatMemoryId()).isEqualTo(MEMORY_ID);
            assertThat(request.invocationContext().methodName()).isEqualTo("chat");
        });
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_output_request_carries_the_same_parameters(Mode mode) {
        var requests = new CopyOnWriteArrayList<ToolOutputGuardrailRequest>();

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, SearchTool.echoing());
            services.toolOutputGuardrails(output(request -> {
                requests.add(request);
                return ToolOutputGuardrailResult.success();
            }));
        });

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(requests).singleElement().satisfies(request -> {
            assertParams(request.requestParams(), run);
            assertThat(request.memoryId()).isEqualTo(MEMORY_ID);
        });
    }

    private static void assertParams(GuardrailRequestParams params, ToolGuardrailTestSupport.Run run) {
        // the real chat memory of the invocation, not a copy, so a guardrail can inspect the conversation
        assertThat(params.chatMemory()).isSameAs(run.memory);
        assertThat(params.userMessageTemplate()).isEqualTo(TEMPLATE);
        // the variables are those of the AI service method (its unnamed @MemoryId parameter is among them, as for the
        // guardrails of the AI service)
        assertThat(params.variables()).containsEntry("topic", TOPIC);
    }

    // ---------- events ----------

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_events_reach_the_registered_listeners_and_the_invocation_summary(Mode mode) {
        var run = run(mode, services -> {
            ToolKind.LIST.register(services, SearchTool.echoing());
            services.toolInputGuardrails(input(request -> ToolInputGuardrailResult.success()))
                    .toolOutputGuardrails(output(request -> ToolOutputGuardrailResult.success()));
        });

        assertThat(run.answerText()).isEqualTo("done");
        assertThat(run.inputGuardrailEvents)
                .singleElement()
                .satisfies(event -> assertThat(event.request().toolName()).isEqualTo("search"));
        assertThat(run.outputGuardrailEvents)
                .singleElement()
                .satisfies(event -> assertThat(event.request().text()).isEqualTo("found {\"q\":\"a\"}"));

        // the summary is fired when the invocation has ended, which a streaming mode does a moment after the answer
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(run.summaryEvents)
                        .singleElement()
                        .satisfies(summary -> {
                            assertThat(summary.events())
                                    .filteredOn(ToolInputGuardrailExecutedEvent.class::isInstance)
                                    .hasSize(1);
                            assertThat(summary.events())
                                    .filteredOn(ToolOutputGuardrailExecutedEvent.class::isInstance)
                                    .hasSize(1);
                        }));
    }

    // ---------- token usage ----------

    private static final TokenUsage TOOL_CALL_USAGE = new TokenUsage(10, 1);
    private static final TokenUsage ANSWER_USAGE = new TokenUsage(100, 20);

    interface FutureResultAssistant {

        @UserMessage(TEMPLATE)
        CompletableFuture<Result<String>> chat(@MemoryId String memoryId, @V("topic") String topic);
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_token_usage_of_an_invocation_with_a_guarded_tool_call_is_the_sum_of_its_model_responses(Mode mode) {
        var tool = SearchTool.echoing();
        Consumer<AiServices<?>> guardedTool = services -> {
            ToolKind.LIST.register(services, tool);
            services.toolInputGuardrails(input(request -> ToolInputGuardrailResult.successWith(REWRITTEN_ARGUMENTS)))
                    .toolOutputGuardrails(output(request -> ToolOutputGuardrailResult.successWith("redacted")));
        };

        var response = finalResponseOf(mode, guardedTool);

        assertThat(tool.receivedArguments).containsExactly(REWRITTEN_ARGUMENTS);
        assertThat(response.aiMessage().text()).isEqualTo("done");
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(110, 21, 131));
    }

    /**
     * Invokes an AI service in the given mode against a model that calls {@code search} and then answers
     * {@code done}, each response with its own token usage, and returns the final response the mode gives: the
     * {@code Result} of the blocking and the {@code CompletableFuture} modes, the completion response of the
     * {@code TokenStream}, and the {@code FinalResponseEvent} of the reactive mode.
     */
    private static ChatResponse finalResponseOf(Mode mode, Consumer<AiServices<?>> configuration) {
        var toolCall = ChatResponse.builder()
                .aiMessage(AiMessage.from(searchCall()))
                .tokenUsage(TOOL_CALL_USAGE)
                .build();
        var answer = ChatResponse.builder()
                .aiMessage(AiMessage.from("done"))
                .tokenUsage(ANSWER_USAGE)
                .build();
        var finalResponse = new CompletableFuture<ChatResponse>();
        // the methods have a @MemoryId, which needs a chat memory provider
        Consumer<AiServices<?>> configure = services -> configuration.accept(
                services.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20)));

        switch (mode) {
            case SYNC -> {
                var services = AiServices.builder(ToolGuardrailTestSupport.SyncAssistant.class)
                        .chatModel(MockChatModel.thatResponds(toolCall).thenResponds(answer));
                configure.accept(services);
                finalResponse.complete(asResponse(services.build().chat(MEMORY_ID, TOPIC)));
            }
            case COMPLETABLE_FUTURE -> {
                var services = AiServices.builder(FutureResultAssistant.class)
                        .chatModel(MockChatModel.thatResponds(toolCall).thenResponds(answer));
                configure.accept(services);
                services.build()
                        .chat(MEMORY_ID, TOPIC)
                        .thenApply(ToolGuardrailContextTests::asResponse)
                        .whenComplete((response, error) -> {
                            if (error != null) {
                                finalResponse.completeExceptionally(error);
                            } else {
                                finalResponse.complete(response);
                            }
                        });
            }
            case TOKEN_STREAM -> {
                var services = AiServices.builder(ToolGuardrailTestSupport.StreamingAssistant.class)
                        .streamingChatModel(
                                MockStreamingChatModel.thatResponds(toolCall).thenResponds(answer));
                configure.accept(services);
                services.build()
                        .chat(MEMORY_ID, TOPIC)
                        .onPartialResponse(ignored -> {})
                        .onCompleteResponse(finalResponse::complete)
                        .onError(finalResponse::completeExceptionally)
                        .start();
            }
            case REACTIVE -> {
                var services = AiServices.builder(ToolGuardrailTestSupport.ReactiveAssistant.class)
                        .streamingChatModel(
                                MockStreamingChatModel.thatResponds(toolCall).thenResponds(answer));
                configure.accept(services);
                services.build().chat(MEMORY_ID, TOPIC).subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(AiServiceStreamingEvent event) {
                        if (event instanceof FinalResponseEvent finalResponseEvent) {
                            finalResponse.complete(finalResponseEvent.chatResponse());
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        finalResponse.completeExceptionally(error);
                    }

                    @Override
                    public void onComplete() {
                        finalResponse.completeExceptionally(new AssertionError("completed without a final response"));
                    }
                });
            }
        }

        assertThat(finalResponse).succeedsWithin(TIMEOUT);
        return finalResponse.join();
    }

    private static ChatResponse asResponse(Result<String> result) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(result.content()))
                .tokenUsage(result.tokenUsage())
                .build();
    }

    @Test
    void the_events_are_not_delivered_to_the_listeners_of_the_ai_service_guardrails() {
        var events = new CopyOnWriteArrayList<AiServiceEvent>();

        run(Mode.SYNC, services -> {
            ToolKind.LIST.register(services, SearchTool.echoing());
            services.toolInputGuardrails(input(request -> ToolInputGuardrailResult.success()))
                    .registerListener(
                            (InputGuardrailExecutedListener) events::add)
                    .registerListener(
                            (OutputGuardrailExecutedListener) events::add);
        });

        assertThat(events).isEmpty();
    }

    // ---------- tool source ----------

    @ParameterizedTest
    @EnumSource(ToolKind.class)
    void the_tool_source_is_set_by_the_framework_for_each_kind_of_tool(ToolKind kind) {
        var sources = new CopyOnWriteArrayList<ToolSource>();
        var outputSources = new CopyOnWriteArrayList<ToolSource>();

        run(Mode.SYNC, services -> {
            kind.register(services, SearchTool.echoing());
            services.toolInputGuardrails(input(request -> {
                        sources.add(request.toolSource());
                        return ToolInputGuardrailResult.success();
                    }))
                    .toolOutputGuardrails(output(request -> {
                        outputSources.add(request.toolSource());
                        return ToolOutputGuardrailResult.success();
                    }));
        });

        assertThat(sources).singleElement().satisfies(source -> assertSourceOf(kind, source));
        assertThat(outputSources).singleElement().satisfies(source -> assertSourceOf(kind, source));
    }

    private static void assertSourceOf(ToolKind kind, ToolSource source) {
        if (kind == ToolKind.ANNOTATED) {
            assertThat(source).isInstanceOfSatisfying(ToolSource.Local.class, local -> {
                assertThat(local.declaringClassName())
                        .isEqualTo(ToolGuardrailTestSupport.AnnotatedSearch.class.getName());
                assertThat(local.methodName()).isEqualTo("search");
                assertThat(local.parameterTypeNames()).containsExactly(String.class.getName());
            });
        } else {
            // a tool without a method, and no source set by the application
            assertThat(source).isEqualTo(ToolSource.programmatic());
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void the_application_can_set_the_source_of_a_tool_and_a_global_guardrail_can_route_on_it(Mode mode) {
        var tool = SearchTool.echoing();

        var run = run(mode, services -> {
            ToolKind.LIST.register(services, tool, builder -> builder.toolSource(ToolSource.mcp("server-1")));
            services.toolInputGuardrails(input(
                    request -> request.toolSource() instanceof ToolSource.Mcp mcp && mcp.involves("server-1")
                            ? ToolInputGuardrailResult.successWith("{\"q\":\"from-server-1\"}")
                            : ToolInputGuardrailResult.success()));
        });

        assertThat(tool.receivedArguments).containsExactly("{\"q\":\"from-server-1\"}");
        assertThat(run.answerText()).isEqualTo("done");
    }

    // ---------- an AI service used as a tool ----------

    /**
     * An AI service that another one uses as a tool. {@link InputGuardrails} guards what the user (here: the outer
     * model) sends to this AI service, {@link ToolInputGuardrails} guards the arguments of the tool call that reaches it.
     */
    @InputGuardrails(AiServiceSideGuardrail.class)
    @ToolInputGuardrails(ToolSideGuardrail.class)
    public interface PolicyExpert {

        @Tool("Answers policy questions")
        @UserMessage("Policy question: {{question}}")
        String answer(@P(name = "question") @V("question") String question);
    }

    /** Counts the user messages of the AI service that reach it. */
    public static class AiServiceSideGuardrail implements InputGuardrail {
        final List<String> userMessages = new CopyOnWriteArrayList<>();

        @Override
        public InputGuardrailResult validate(dev.langchain4j.data.message.UserMessage um) {
            userMessages.add(um.singleText());
            return success();
        }
    }

    /** Counts the tool calls that reach it. */
    public static class ToolSideGuardrail extends BlockingToolInputGuardrail {
        final List<String> toolNames = new CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            calls.incrementAndGet();
            toolNames.add(request.toolName());
            return success();
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = Mode.class,
            names = {"SYNC", "COMPLETABLE_FUTURE"})
    void on_an_ai_service_used_as_a_tool_the_two_kinds_of_input_guardrails_each_apply_to_their_own_side(Mode mode) {
        var expert = createAiService(
                PolicyExpert.class,
                builder -> builder.chatModel(MockChatModel.thatResponds("Yes, up to 7 days before the start date.")));
        var aiServiceSide = SingletonClassInstanceFactory.getInstance(AiServiceSideGuardrail.class);
        var toolSide = SingletonClassInstanceFactory.getInstance(ToolSideGuardrail.class);
        var toolCall = ToolGuardrailTestSupport.toolCall("1", "answer", "{\"question\":\"Can I cancel?\"}");

        var run = run(
                mode,
                List.of(
                        AiMessage.from(toolCall),
                        AiMessage.from("done")),
                services -> services.tools(expert));

        assertThat(run.answerText()).isEqualTo("done");
        // the tool guardrail ran once, for the tool call of the outer AI service...
        assertThat(toolSide.calls).hasValue(1);
        assertThat(toolSide.toolNames).containsExactly("answer");
        // ...and the AI service guardrail once, for the user message that the tool call sent to the inner AI service
        assertThat(aiServiceSide.userMessages).containsExactly("Policy question: Can I cancel?");
        assertThat(run.toolResultMessageOfSecondRequest("answer").text())
                .isEqualTo("Yes, up to 7 days before the start date.");
    }
}
