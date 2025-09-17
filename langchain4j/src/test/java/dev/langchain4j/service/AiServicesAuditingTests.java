package dev.langchain4j.service;

import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.audit.api.AiServiceInteractionEventListenerRegistrar;
import dev.langchain4j.audit.api.event.AiServiceInteractionCompletedEvent;
import dev.langchain4j.audit.api.event.AiServiceInteractionErrorEvent;
import dev.langchain4j.audit.api.event.AiServiceInteractionEvent;
import dev.langchain4j.audit.api.event.AiServiceInteractionStartedEvent;
import dev.langchain4j.audit.api.event.AiServiceInvocationContext;
import dev.langchain4j.audit.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.audit.api.event.GuardrailExecutedEvent;
import dev.langchain4j.audit.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.audit.api.event.OutputGuardrailExecutedEvent;
import dev.langchain4j.audit.api.event.ToolExecutedEvent;
import dev.langchain4j.audit.api.listener.AiServiceInteractionCompletedEventListener;
import dev.langchain4j.audit.api.listener.AiServiceInteractionEventListener;
import dev.langchain4j.audit.api.listener.AiServiceInteractionStartedEventListener;
import dev.langchain4j.audit.api.listener.AiServiceResponseReceivedEventListener;
import dev.langchain4j.audit.api.listener.InputGuardrailExecutedEventListener;
import dev.langchain4j.audit.api.listener.LLMInteractionErrorEventListener;
import dev.langchain4j.audit.api.listener.OutputGuardrailExecutedEventListener;
import dev.langchain4j.audit.api.listener.ToolExecutedEventListener;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiServicesAuditingTests {
    private static final String DEFAULT_SCENARIO = "default";
    private static final String DEFAULT_EXPECTED_RESPONSE = "Hello how are you today?";
    private static final String TOOLS_SCENARIO = "tools";
    private static final String TOOLS_SECOND_SCENARIO = "second";
    private static final String TOOL_USER_MESSAGE =
            "What is the square root of 485906798473894056 in scientific notation?";
    private static final String TOOL_EXPECTED_RESPONSE =
            "The square root of 485,906,798,473,894,056 in scientific notation is approximately 6.97070153193991E8.";

    Map<Class<? extends AiServiceInteractionEvent>, MyEventListener<? extends AiServiceInteractionEvent>> listeners =
            createListeners();
    private WireMockServer wireMock;

    private void runScenario(
            Supplier<Assistant> assistantCreator,
            Consumer<Assistant> chatAssertion,
            String expectedMethodName,
            List<Class<? extends AiServiceInteractionEvent>> noEventsReceivedClasses,
            String expectedUserMessage,
            List<Class<? extends AiServiceInteractionEvent>> expectedEventsReceivedClasses) {

        // Create the assistant
        var assistant = assistantCreator.get();

        // Invoke the operation prior to registering the listeners
        // Nothing should happen
        chatAssertion.accept(assistant);
        assertNoEventsReceived(7, listeners.values());

        // Now register the events
        registerAllListeners();

        // Let's invoke the operation a few times
        IntStream.range(0, 5).forEach(i -> chatAssertion.accept(assistant));

        assertNoEventsReceived(
                noEventsReceivedClasses.size(),
                noEventsReceivedClasses.stream().map(listeners::get).toList());

        // There should be 1 started, 1 complete, 1 response received, 1 tool invocation
        assertEventsReceived(
                expectedEventsReceivedClasses.size(),
                expectedUserMessage,
                expectedMethodName,
                expectedEventsReceivedClasses.stream().map(listeners::get).toList());

        // Now unregister
        unregisterAllListeners();

        // Nothing should happen when invoking the operation again
        chatAssertion.accept(assistant);
        assertNoEventsReceived(7, listeners.values());
    }

    @Test
    void failureChat() {
        runScenario(
                () -> Assistant.createFailingService(),
                assistant ->
                        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> assistant.chat("Hello!")),
                "chat",
                List.of(
                        AiServiceInteractionCompletedEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(AiServiceInteractionStartedEvent.class, AiServiceInteractionErrorEvent.class));
    }

    @Test
    void successfulChatNoTools() {
        runScenario(
                () -> Assistant.create(false, this.wireMock),
                assistant -> assertThat(assistant.chat("Hello!")).isEqualTo(DEFAULT_EXPECTED_RESPONSE),
                "chat",
                List.of(
                        AiServiceInteractionErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInteractionStartedEvent.class,
                        AiServiceInteractionCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    @Test
    void successfulChatWithTools() {
        runScenario(
                () -> Assistant.create(true, this.wireMock),
                assistant -> {
                    // Need to reset wiremock's state before each invocation
                    this.wireMock.setScenarioState(TOOLS_SCENARIO, Scenario.STARTED);
                    assertThat(assistant.chat(TOOL_USER_MESSAGE)).isEqualTo(TOOL_EXPECTED_RESPONSE);
                },
                "chat",
                List.of(
                        AiServiceInteractionErrorEvent.class,
                        InputGuardrailExecutedEvent.class,
                        OutputGuardrailExecutedEvent.class),
                TOOL_USER_MESSAGE,
                List.of(
                        AiServiceInteractionStartedEvent.class,
                        AiServiceInteractionCompletedEvent.class,
                        AiServiceResponseReceivedEvent.class,
                        ToolExecutedEvent.class));
    }

    @Test
    void auditingWithInputGuardrails() {
        runScenario(
                () -> Assistant.create(false, this.wireMock),
                assistant -> assertThatExceptionOfType(InputGuardrailException.class)
                        .isThrownBy(() -> assistant.chatWithInputGuardrails("Hello!"))
                        .withMessage(
                                "The guardrail %s failed with this message: User message is not valid",
                                FailureInputGuardrail.class.getName()),
                "chatWithInputGuardrails",
                List.of(
                        AiServiceInteractionCompletedEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInteractionStartedEvent.class,
                        AiServiceInteractionErrorEvent.class,
                        InputGuardrailExecutedEvent.class));
    }

    @Test
    void auditingWithOutputGuardrails() {
        runScenario(
                () -> Assistant.create(false, this.wireMock),
                assistant -> assertThatExceptionOfType(OutputGuardrailException.class)
                        .isThrownBy(() -> assistant.chatWithOutputGuardrails("Hello!"))
                        .withMessage(
                                "The guardrail %s failed with this message: LLM response is not valid",
                                FailureOutputGuardrail.class.getName()),
                "chatWithOutputGuardrails",
                List.of(
                        AiServiceInteractionCompletedEvent.class,
                        InputGuardrailExecutedEvent.class,
                        ToolExecutedEvent.class),
                "Hello!",
                List.of(
                        AiServiceInteractionStartedEvent.class,
                        AiServiceInteractionErrorEvent.class,
                        OutputGuardrailExecutedEvent.class,
                        AiServiceResponseReceivedEvent.class));
    }

    private void registerAllListeners() {
        listeners.values().forEach(AiServiceInteractionEventListenerRegistrar.getInstance()::register);
    }

    private void unregisterAllListeners() {
        listeners.values().forEach(listener -> {
            AiServiceInteractionEventListenerRegistrar.getInstance().unregister(listener);
            listener.reset();
        });
    }

    private static void assertNoEventsReceived(
            int expectedSize, Collection<? extends MyEventListener<? extends AiServiceInteractionEvent>> listeners) {
        assertThat(listeners).isNotNull().hasSize(expectedSize).allSatisfy(l -> assertThat(l)
                .isNotNull()
                .extracting(MyEventListener::count, MyEventListener::event)
                .containsExactly(0, null));
    }

    private static void assertEventsReceived(
            int expectedSize,
            String expectedUserMessage,
            String expectedMethodName,
            Collection<? extends MyEventListener<? extends AiServiceInteractionEvent>> listeners) {

        // All the events have the correct number of invocations & non-null events
        assertThat(listeners).isNotNull().hasSize(expectedSize).allSatisfy(l -> {
            assertThat(l)
                    .isNotNull()
                    .extracting(MyEventListener::count)
                    .isEqualTo(GuardrailExecutedEvent.class.isAssignableFrom(l.getEventClass()) ? 10 : 5);

            assertThat(l).isNotNull().extracting(MyEventListener::event).isNotNull();
        });

        var firstInteractionSource = listeners.stream()
                .map(MyEventListener::event)
                .map(AiServiceInteractionEvent::invocationContext)
                .findFirst();

        var is = assertThat(firstInteractionSource).get().actual();

        // Verify that all the listeners have the same invocationContext()
        assertThat(listeners)
                .extracting(l -> l.event().invocationContext())
                .usingRecursiveFieldByFieldElementComparator()
                .containsOnly(is);

        // And because all the invocationContext() is the same, verify that it has the correct information
        assertThat(is)
                .isNotNull()
                .extracting(
                        AiServiceInvocationContext::interfaceName,
                        AiServiceInvocationContext::methodName,
                        AiServiceInvocationContext::methodArguments,
                        AiServiceInvocationContext::memoryId)
                .containsExactly(
                        Assistant.class.getName(), expectedMethodName, List.of(expectedUserMessage), Optional.empty());

        assertThat(is.interactionId()).isNotNull();
        assertThat(is.timestamp()).isNotNull();
    }

    private static Map<Class<? extends AiServiceInteractionEvent>, MyEventListener<? extends AiServiceInteractionEvent>>
            createListeners() {

        return Stream.of(
                        new MyInputGuardrailExecutedEventListener(),
                        new MyLLMInteractionCompletedEventListener(),
                        new MyLLMInteractionErrorEventListener(),
                        new MyLLMInteractionStartedEventListener(),
                        new MyLLMResponseReceivedEventListener(),
                        new MyOutputGuardrailExecutedEventListener(),
                        new MyToolExecutedEventListener())
                .collect(Collectors.toMap(AiServiceInteractionEventListener::getEventClass, Function.identity()));
    }

    @BeforeEach
    void beforeEach() {
        this.wireMock = new WireMockServer(wireMockConfig().dynamicPort());

        var firstToolResponse =
                """
                {
                  "id": "chatcmpl-8D88Dag1gAKnOPP9Ed4bos7vSpaNz",
                  "object": "chat.completion",
                  "created": 1698140213,
                  "model": "gpt-5-mini-2025-08-07",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "function_call": {
                          "name": "squareRoot",
                          "arguments": "{\\n  \\"arg0\\": 485906798473894056\\n}"
                        }
                      },
                      "finish_reason": "function_call"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 65,
                    "completion_tokens": 20,
                    "total_tokens": 85
                  }
                }
                """;

        var secondToolResponse =
                """
                        {
                          "id": "chatcmpl-8D88FIAUWSpwLaShFr0w8G1SWuVdl",
                          "object": "chat.completion",
                          "created": 1698140215,
                          "model": "gpt-5-mini-2025-08-07",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "%s"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 102,
                            "completion_tokens": 33,
                            "total_tokens": 135
                          }
                        }
                """
                        .formatted(TOOL_EXPECTED_RESPONSE);

        var noToolResponse =
                """
                        {
                          "id": "chatcmpl-8D88FIAUWSpwLaShFr0w1SWuVdl",
                          "object": "chat.completion",
                          "created": 1698140215,
                          "model": "gpt-5-mini-2025-08-07",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "Hello how are you today?"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 102,
                            "completion_tokens": 33,
                            "total_tokens": 135
                          }
                        }
                """;

        var toolRequestBodyMatcher = and(
                matchingJsonPath("$.messages[0].role", equalTo("user")),
                matchingJsonPath("$.messages[0].content", equalTo(TOOL_USER_MESSAGE)));

        this.wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario(TOOLS_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .withRequestBody(toolRequestBodyMatcher)
                .willReturn(okJson(firstToolResponse)));

        this.wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario(TOOLS_SCENARIO)
                .whenScenarioStateIs(TOOLS_SECOND_SCENARIO)
                .withRequestBody(toolRequestBodyMatcher)
                .willReturn(okJson(secondToolResponse)));

        this.wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario(DEFAULT_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .withRequestBody(and(
                        matchingJsonPath("$.messages[0].role", equalTo("user")),
                        matchingJsonPath("$.messages[0].content", equalTo("Hello!"))))
                .willReturn(okJson(noToolResponse)));

        this.wireMock.setScenarioState(DEFAULT_SCENARIO, Scenario.STARTED);
        this.wireMock.setScenarioState(TOOLS_SCENARIO, Scenario.STARTED);

        this.wireMock.start();
    }

    @AfterEach
    void afterEach() {
        Optional.ofNullable(this.wireMock).ifPresent(WireMockServer::stop);
    }

    @SystemMessage("You are a chat bot that answers questions")
    interface Assistant {
        String chat(String message);

        @InputGuardrails({SuccessInputGuardrail.class, FailureInputGuardrail.class})
        String chatWithInputGuardrails(String message);

        @OutputGuardrails({SuccessOutputGuardrail.class, FailureOutputGuardrail.class})
        String chatWithOutputGuardrails(String message);

        static Assistant create(boolean shouldHaveToolAccess, WireMockServer wireMock) {
            var builder = AiServices.builder(Assistant.class)
                    .chatModel(OpenAiChatModel.builder()
                            .apiKey("change-me")
                            .baseUrl("%s/v1".formatted(wireMock.baseUrl()))
                            .modelName("gpt-5-mini")
                            .logRequests(true)
                            .logResponses(true)
                            .build());

            if (shouldHaveToolAccess) {
                builder.tools(new Calculator(wireMock));
            }

            return builder.build();
        }

        static Assistant createFailingService() {
            return AiServices.create(Assistant.class, new FailingChatModel());
        }
    }

    public static class Calculator {
        private final WireMockServer wireMock;

        public Calculator(WireMockServer wireMock) {
            this.wireMock = wireMock;
        }

        @Tool("calculates the square root of the provided number")
        public double squareRoot(double number) {
            var result = Math.sqrt(number);
            this.wireMock.setScenarioState(TOOLS_SCENARIO, TOOLS_SECOND_SCENARIO);

            return result;
        }
    }

    public static class SuccessInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return successWith("Success!!");
        }
    }

    public static class FailureInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure("User message is not valid");
        }
    }

    public static class SuccessOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith("Success!!");
        }
    }

    public static class FailureOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure("LLM response is not valid");
        }
    }

    public static class FailingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new RuntimeException("LLM failed!");
        }
    }

    public abstract static class MyEventListener<T extends AiServiceInteractionEvent>
            implements AiServiceInteractionEventListener<T> {
        private final AtomicInteger count = new AtomicInteger();
        private T event;

        @Override
        public void onEvent(T event) {
            this.event = event;
            increment();
        }

        public T event() {
            return event;
        }

        protected void increment() {
            count.incrementAndGet();
        }

        public int count() {
            return count.get();
        }

        public void reset() {
            this.count.set(0);
            this.event = null;
        }
    }

    public static class MyToolExecutedEventListener extends MyEventListener<ToolExecutedEvent>
            implements ToolExecutedEventListener {}

    public static class MyOutputGuardrailExecutedEventListener extends MyEventListener<OutputGuardrailExecutedEvent>
            implements OutputGuardrailExecutedEventListener {}

    public static class MyLLMResponseReceivedEventListener extends MyEventListener<AiServiceResponseReceivedEvent>
            implements AiServiceResponseReceivedEventListener {}

    public static class MyLLMInteractionStartedEventListener extends MyEventListener<AiServiceInteractionStartedEvent>
            implements AiServiceInteractionStartedEventListener {}

    public static class MyLLMInteractionErrorEventListener extends MyEventListener<AiServiceInteractionErrorEvent>
            implements LLMInteractionErrorEventListener {}

    public static class MyLLMInteractionCompletedEventListener
            extends MyEventListener<AiServiceInteractionCompletedEvent>
            implements AiServiceInteractionCompletedEventListener {}

    public static class MyInputGuardrailExecutedEventListener extends MyEventListener<InputGuardrailExecutedEvent>
            implements InputGuardrailExecutedEventListener {}
}
