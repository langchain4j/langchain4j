package dev.langchain4j.service;

import static java.util.Collections.synchronizedList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceErrorListener;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiServicesAsyncTest {

    interface Assistant {

        CompletableFuture<String> chat(String userMessage);
    }

    @Test
    void should_return_completable_future_without_invoking_blocking_chat() throws Exception {

        ChatModelMock chatModel = spy(ChatModelMock.thatAlwaysResponds("Berlin"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .build();

        CompletableFuture<String> future = assistant.chat("What is the capital of Germany?");

        assertThat(future.get(10, SECONDS)).isEqualTo("Berlin");
        assertThat(chatModel.requests()).hasSize(1);
        verify(chatModel).doChatAsync(any());
        verify(chatModel, never()).doChat(any());
    }

    @Test
    void should_not_block_the_calling_thread() throws Exception {

        CompletableFuture<ChatResponse> modelFuture = new CompletableFuture<>();
        ChatModel chatModel = new ChatModel() {

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                return modelFuture;
            }
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .build();

        CompletableFuture<String> future = assistant.chat("Hi");

        assertThat(future).isNotDone();

        modelFuture.complete(ChatResponse.builder()
                .aiMessage(AiMessage.from("Hello"))
                .metadata(ChatResponseMetadata.builder().build())
                .build());

        assertThat(future.get(10, SECONDS)).isEqualTo("Hello");
    }

    static class Tools {

        final AtomicInteger invocations = new AtomicInteger();

        @Tool
        String currentTemperature() {
            invocations.incrementAndGet();
            return "42";
        }
    }

    @Test
    void should_execute_blocking_tools() throws Exception {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = spy(ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees")));
        Tools tools = new Tools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        assertThat(future.get(10, SECONDS)).isEqualTo("It is 42 degrees");
        assertThat(tools.invocations).hasValue(1);
        assertThat(chatModel.requests()).hasSize(2);
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> assertThat(((ToolExecutionResultMessage) message).text())
                        .isEqualTo("42"));
        verify(chatModel, never()).doChat(any());
    }

    static class ConcurrentTools {

        final List<String> executedTools = synchronizedList(new ArrayList<>());

        @Tool
        String currentTemperature() {
            executedTools.add("currentTemperature");
            return "42";
        }

        @Tool
        String currentHumidity() {
            executedTools.add("currentHumidity");
            return "69";
        }
    }

    @Test
    void should_execute_multiple_blocking_tools_concurrently() throws Exception {

        ToolExecutionRequest temperatureRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ToolExecutionRequest humidityRequest = ToolExecutionRequest.builder()
                .id("2")
                .name("currentHumidity")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = spy(ChatModelMock.thatAlwaysResponds(
                AiMessage.from(temperatureRequest, humidityRequest), AiMessage.from("42 degrees, 69 percent")));
        ConcurrentTools tools = new ConcurrentTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .executeToolsConcurrently()
                .build();

        CompletableFuture<String> future = assistant.chat("What is the weather?");

        assertThat(future.get(10, SECONDS)).isEqualTo("42 degrees, 69 percent");
        assertThat(tools.executedTools).containsExactlyInAnyOrder("currentTemperature", "currentHumidity");
        assertThat(chatModel.requests()).hasSize(2);
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .hasSize(2);
        verify(chatModel, never()).doChat(any());
    }

    @Test
    void should_use_chat_memory() throws Exception {

        ChatModelMock chatModel =
                ChatModelMock.thatAlwaysResponds(AiMessage.from("first answer"), AiMessage.from("second answer"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        assertThat(assistant.chat("first question").get(10, SECONDS)).isEqualTo("first answer");
        assertThat(assistant.chat("second question").get(10, SECONDS)).isEqualTo("second answer");

        assertThat(chatModel.requests()).hasSize(2);
        assertThat(chatModel.requests().get(1).messages()).hasSize(3);
        assertThat(chatModel.requests().get(1).messages().get(1))
                .isEqualTo(AiMessage.from("first answer"));
    }

    interface AssistantReturningResult {

        CompletableFuture<Result<String>> chat(String userMessage);
    }

    @Test
    void should_return_result() throws Exception {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Berlin");

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(chatModel)
                .build();

        Result<String> result = assistant.chat("What is the capital of Germany?").get(10, SECONDS);

        assertThat(result.content()).isEqualTo("Berlin");
        assertThat(result.toolExecutions()).isEmpty();
    }

    static class Person {

        String name;
    }

    interface AssistantReturningPojo {

        CompletableFuture<Person> extractPerson(String text);
    }

    @Test
    void should_parse_pojo() throws Exception {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("{\"name\": \"Klaus\"}");

        AssistantReturningPojo assistant = AiServices.builder(AssistantReturningPojo.class)
                .chatModel(chatModel)
                .build();

        Person person = assistant.extractPerson("My name is Klaus").get(10, SECONDS);

        assertThat(person.name).isEqualTo("Klaus");
    }

    interface AssistantReturningResultWithPojo {

        CompletableFuture<Result<Person>> extractPerson(String text);
    }

    @Test
    void should_return_result_with_pojo() throws Exception {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("{\"name\": \"Klaus\"}");

        AssistantReturningResultWithPojo assistant = AiServices.builder(AssistantReturningResultWithPojo.class)
                .chatModel(chatModel)
                .build();

        Result<Person> result = assistant.extractPerson("My name is Klaus").get(10, SECONDS);

        assertThat(result.content().name).isEqualTo("Klaus");
        assertThat(result.toolExecutions()).isEmpty();
        assertThat(result.finalResponse().aiMessage().text()).isEqualTo("{\"name\": \"Klaus\"}");
    }

    interface AssistantReturningList {

        CompletableFuture<List<Person>> extractPeople(String text);
    }

    /**
     * A model that reports JSON-schema support and completes off-thread. Needed for return types whose output
     * parser only supports a JSON schema (e.g. a list of POJOs), where {@link ChatModelMock} (no declared
     * capabilities) would fall back to text format instructions that such a parser does not provide.
     */
    static class JsonSchemaCapableModel implements ChatModel {

        private final String response;

        JsonSchemaCapableModel(String response) {
            this.response = response;
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        }

        @Override
        public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
            return CompletableFuture.supplyAsync(() -> ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .metadata(ChatResponseMetadata.builder().build())
                    .build());
        }
    }

    @Test
    void should_parse_list_of_pojo__async() throws Exception {

        // CompletableFuture<List<Person>>: the async unwrap must preserve the nested generic (List<Person>,
        // not raw List) so the output parser can resolve the element type
        ChatModel chatModel =
                new JsonSchemaCapableModel("{\"values\":[{\"name\":\"Klaus\"},{\"name\":\"Franny\"}]}");

        AssistantReturningList assistant = AiServices.builder(AssistantReturningList.class)
                .chatModel(chatModel)
                .build();

        List<Person> people = assistant.extractPeople("Klaus and Franny").get(10, SECONDS);

        assertThat(people).extracting(person -> person.name).containsExactly("Klaus", "Franny");
    }

    enum Sentiment {
        POSITIVE,
        NEGATIVE
    }

    interface AssistantReturningEnum {

        CompletableFuture<Sentiment> analyzeSentiment(String text);
    }

    @Test
    void should_parse_enum__async() throws Exception {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("POSITIVE");

        AssistantReturningEnum assistant = AiServices.builder(AssistantReturningEnum.class)
                .chatModel(chatModel)
                .build();

        assertThat(assistant.analyzeSentiment("I love it!").get(10, SECONDS)).isEqualTo(Sentiment.POSITIVE);
    }

    interface AssistantReturningBoolean {

        CompletableFuture<Boolean> isPositive(String text);
    }

    @Test
    void should_parse_boolean__async() throws Exception {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("true");

        AssistantReturningBoolean assistant = AiServices.builder(AssistantReturningBoolean.class)
                .chatModel(chatModel)
                .build();

        assertThat(assistant.isPositive("I love it!").get(10, SECONDS)).isTrue();
    }

    @Test
    void should_return_result_with_tool_executions__async() throws Exception {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees"));

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(chatModel)
                .tools(new Tools())
                .build();

        Result<String> result = assistant.chat("What is the temperature?").get(10, SECONDS);

        assertThat(result.content()).isEqualTo("It is 42 degrees");
        assertThat(result.toolExecutions()).singleElement().satisfies(execution -> {
            assertThat(execution.request().name()).isEqualTo("currentTemperature");
            assertThat(execution.result()).isEqualTo("42");
        });
        assertThat(result.intermediateResponses())
                .singleElement()
                .satisfies(response ->
                        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue());
    }

    @Test
    void should_fail_future_when_model_fails() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysThrowsExceptionWithMessage("boom");

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .build();

        CompletableFuture<String> future = assistant.chat("Hi");

        assertThatThrownBy(() -> future.get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseMessage("boom");
    }

    // Exception fidelity: future.get() must yield ExecutionException whose direct cause is the original
    // exception - no CompletionException leaking through from the internal CompletableFuture composition.

    static class CustomException extends RuntimeException {

        CustomException(String message) {
            super(message);
        }
    }

    @Test
    void model_error_propagates_original_cause_without_completion_exception() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysThrowsExceptionWithMessage("boom");

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .build();

        CompletableFuture<String> future = assistant.chat("Hi");

        ExecutionException executionException = assertThrows(ExecutionException.class, () -> future.get(10, SECONDS));
        assertThat(executionException.getCause())
                .isNotInstanceOf(CompletionException.class)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");
    }

    static class ThrowingTools {

        @Tool
        String currentTemperature() {
            throw new IllegalStateException("tool failure");
        }
    }

    @Test
    void tool_error_propagates_original_cause_without_completion_exception() {

        CustomException rethrown = new CustomException("rethrown by handler");
        ToolExecutionErrorHandler rethrowingHandler = (error, context) -> {
            throw rethrown;
        };

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel =
                ChatModelMock.thatAlwaysResponds(AiMessage.from(toolExecutionRequest), AiMessage.from("ignored"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new ThrowingTools())
                .toolExecutionErrorHandler(rethrowingHandler)
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        ExecutionException executionException = assertThrows(ExecutionException.class, () -> future.get(10, SECONDS));
        assertThat(executionException.getCause())
                .isNotInstanceOf(CompletionException.class)
                .isSameAs(rethrown);
    }

    public static class FailingOutputGuardrail implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure("output is not valid");
        }
    }

    interface OutputGuardedAssistant {

        @OutputGuardrails(FailingOutputGuardrail.class)
        CompletableFuture<String> chat(String userMessage);
    }

    @Test
    void output_guardrail_error_propagates_original_cause_without_completion_exception() {

        OutputGuardedAssistant assistant = AiServices.builder(OutputGuardedAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("Berlin"))
                .build();

        CompletableFuture<String> future = assistant.chat("What is the capital of Germany?");

        ExecutionException executionException = assertThrows(ExecutionException.class, () -> future.get(10, SECONDS));
        assertThat(executionException.getCause())
                .isNotInstanceOf(CompletionException.class)
                .isInstanceOf(OutputGuardrailException.class)
                .hasMessageContaining("output is not valid");
    }

    @Test
    void should_release_caller_immediately_when_future_is_cancelled() {

        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel chatModel = new ChatModel() {

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                modelCalls.incrementAndGet();
                return new CompletableFuture<>(); // never completes
            }
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .build();

        CompletableFuture<String> future = assistant.chat("Hi");
        assertThat(future).isNotDone();

        boolean cancelled = future.cancel(true);

        // the caller is released immediately, without waiting for the (never-completing) model call
        assertThat(cancelled).isTrue();
        assertThat(future).isCancelled();
        assertThatThrownBy(() -> future.get(1, SECONDS)).isInstanceOf(CancellationException.class);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void should_stop_tool_loop_when_future_is_cancelled() throws Exception {

        AtomicInteger modelCalls = new AtomicInteger();
        CountDownLatch secondModelCall = new CountDownLatch(1);
        CompletableFuture<String> toolGate = new CompletableFuture<>();

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("gatedTool")
                .arguments("{}")
                .build();

        ChatModel chatModel = new ChatModel() {

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                if (modelCalls.incrementAndGet() == 1) {
                    return CompletableFuture.completedFuture(ChatResponse.builder()
                            .aiMessage(AiMessage.from(toolExecutionRequest))
                            .metadata(ChatResponseMetadata.builder().build())
                            .build());
                }
                secondModelCall.countDown();
                return CompletableFuture.completedFuture(ChatResponse.builder()
                        .aiMessage(AiMessage.from("done"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build());
            }
        };

        class GatedTools {

            @Tool
            CompletableFuture<String> gatedTool() {
                return toolGate; // keeps the tool loop parked until the test completes it
            }
        }

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new GatedTools())
                .build();

        CompletableFuture<String> future = assistant.chat("go");

        // round 1 completed and the loop is now parked on the gated tool, before the round-2 model call
        assertThat(modelCalls).hasValue(1);
        assertThat(future).isNotDone();

        future.cancel(true);
        toolGate.complete("42"); // release the tool; the loop must NOT proceed to a second model call

        assertThat(future).isCancelled();
        assertThat(secondModelCall.await(1, SECONDS))
                .as("no further model call must be issued after cancellation")
                .isFalse();
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void should_not_fire_completion_or_error_event_when_future_is_cancelled() {

        AtomicInteger completedEvents = new AtomicInteger();
        AtomicInteger errorEvents = new AtomicInteger();

        ChatModel chatModel = new ChatModel() {

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                return new CompletableFuture<>(); // never completes
            }
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .registerListeners(List.of(
                        (AiServiceCompletedListener) event -> completedEvents.incrementAndGet(),
                        (AiServiceErrorListener) event -> errorEvents.incrementAndGet()))
                .build();

        CompletableFuture<String> future = assistant.chat("Hi");
        future.cancel(true);

        assertThat(future).isCancelled();
        // cancellation is neither a successful completion nor an error
        assertThat(completedEvents).hasValue(0);
        assertThat(errorEvents).hasValue(0);
    }

    @Test
    void should_execute_blocking_tools_in_multiple_rounds() throws Exception {

        ToolExecutionRequest firstRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ToolExecutionRequest secondRequest = ToolExecutionRequest.builder()
                .id("2")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(firstRequest), AiMessage.from(secondRequest), AiMessage.from("It is 42 degrees"));
        Tools tools = new Tools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        assertThat(future.get(10, SECONDS)).isEqualTo("It is 42 degrees");
        assertThat(tools.invocations).hasValue(2);
        assertThat(chatModel.requests()).hasSize(3);
        assertThat(chatModel.requests().get(0).messages()).hasSize(1);
        assertThat(chatModel.requests().get(1).messages()).hasSize(3);
        assertThat(chatModel.requests().get(2).messages()).hasSize(5);
    }

    @Test
    void should_fail_future_when_max_tool_calling_round_trips_exceeded() {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatResponds(ignored -> AiMessage.from(toolExecutionRequest));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new Tools())
                .maxToolCallingRoundTrips(2)
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        assertThatThrownBy(() -> future.get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause()
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("exceeded 2 tool calling round trips");
    }

    @Test
    void should_fail_future_when_hallucinated_tool_is_requested() {

        ToolExecutionRequest hallucinatedRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("unknownTool")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(AiMessage.from(hallucinatedRequest));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new Tools())
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        assertThatThrownBy(() -> future.get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause()
                .hasMessageContaining("no such tool exists");
    }

    @Test
    void should_aggregate_token_usage_across_tool_rounds() throws Exception {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        Queue<ChatResponse> responses = new ConcurrentLinkedQueue<>(List.of(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(toolExecutionRequest))
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(new TokenUsage(10, 20))
                                .build())
                        .build(),
                ChatResponse.builder()
                        .aiMessage(AiMessage.from("It is 42 degrees"))
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(new TokenUsage(5, 5))
                                .build())
                        .build()));
        ChatModel chatModel = new ChatModel() {

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                return CompletableFuture.supplyAsync(responses::poll);
            }
        };

        AssistantReturningResult assistant = AiServices.builder(AssistantReturningResult.class)
                .chatModel(chatModel)
                .tools(new Tools())
                .build();

        Result<String> result = assistant.chat("What is the temperature?").get(10, SECONDS);

        assertThat(result.content()).isEqualTo("It is 42 degrees");
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(15, 25));
    }

    public static class FailingInputGuardrail implements InputGuardrail {

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure("User message is not valid");
        }
    }

    interface AssistantWithInputGuardrail {

        @InputGuardrails(FailingInputGuardrail.class)
        CompletableFuture<String> chat(String userMessage);
    }

    @Test
    void should_throw_input_guardrail_exception_synchronously() {

        // Input guardrails run before any I/O is initiated, so a violation is thrown synchronously
        // from the AI Service method (like a configuration error), not delivered via the future.
        // This test pins that behavior: changing it to a failed future should be a deliberate decision.
        AssistantWithInputGuardrail assistant = AiServices.builder(AssistantWithInputGuardrail.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("Berlin"))
                .build();

        assertThatThrownBy(() -> assistant.chat("Hi"))
                .isInstanceOf(InputGuardrailException.class)
                .hasMessageContaining("User message is not valid");
    }

    static class AsyncTools {

        final AtomicInteger invocations = new AtomicInteger();

        @Tool
        CompletableFuture<String> currentTemperature() {
            invocations.incrementAndGet();
            return CompletableFuture.supplyAsync(() -> "42", CompletableFuture.delayedExecutor(50, MILLISECONDS));
        }
    }

    @Test
    void should_execute_tool_returning_completable_future() throws Exception {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees"));
        AsyncTools tools = new AsyncTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        assertThat(future.get(10, SECONDS)).isEqualTo("It is 42 degrees");
        assertThat(tools.invocations).hasValue(1);
        assertThat(chatModel.requests()).hasSize(2);
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> assertThat(((ToolExecutionResultMessage) message).text())
                        .isEqualTo("42"));
    }

    static class ConcurrentAsyncTools {

        private final Executor executor;
        final CyclicBarrier bothInFlight = new CyclicBarrier(2);
        final List<String> executedTools = synchronizedList(new ArrayList<>());

        ConcurrentAsyncTools(Executor executor) {
            this.executor = executor;
        }

        @Tool
        CompletableFuture<String> currentTemperature() {
            executedTools.add("currentTemperature");
            return CompletableFuture.supplyAsync(
                    () -> {
                        awaitBarrier(bothInFlight);
                        return "42";
                    },
                    executor);
        }

        @Tool
        CompletableFuture<String> currentHumidity() {
            executedTools.add("currentHumidity");
            return CompletableFuture.supplyAsync(
                    () -> {
                        awaitBarrier(bothInFlight);
                        return "69";
                    },
                    executor);
        }
    }

    @Test
    void should_execute_multiple_async_tools_in_parallel() throws Exception {

        // Both async tools complete only after a shared CyclicBarrier(2) trips, which requires both to be
        // in flight at the same time - a deterministic proof they run in parallel. A sequential tool loop
        // would never trip the barrier and would time out. A dedicated 2-thread pool guarantees both
        // futures have a thread to run on (independent of the common pool's size).
        ExecutorService toolPool = Executors.newFixedThreadPool(2);
        try {
            ToolExecutionRequest temperatureRequest = ToolExecutionRequest.builder()
                    .id("1")
                    .name("currentTemperature")
                    .arguments("{}")
                    .build();
            ToolExecutionRequest humidityRequest = ToolExecutionRequest.builder()
                    .id("2")
                    .name("currentHumidity")
                    .arguments("{}")
                    .build();
            ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                    AiMessage.from(temperatureRequest, humidityRequest), AiMessage.from("42 degrees, 69 percent"));
            ConcurrentAsyncTools tools = new ConcurrentAsyncTools(toolPool);

            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(chatModel)
                    .tools(tools)
                    .executeToolsConcurrently()
                    .build();

            String answer = assistant.chat("What is the weather?").get(10, SECONDS);

            assertThat(answer).isEqualTo("42 degrees, 69 percent");
            assertThat(tools.executedTools).containsExactlyInAnyOrder("currentTemperature", "currentHumidity");
            assertThat(chatModel.requests().get(1).messages())
                    .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                    .extracting(message -> ((ToolExecutionResultMessage) message).text())
                    .containsExactlyInAnyOrder("42", "69");
        } finally {
            toolPool.shutdownNow();
        }
    }

    static class MixedTools {

        final List<String> executedTools = synchronizedList(new ArrayList<>());

        @Tool
        CompletableFuture<String> currentTemperature() {
            executedTools.add("currentTemperature");
            return CompletableFuture.supplyAsync(() -> "42", CompletableFuture.delayedExecutor(50, MILLISECONDS));
        }

        @Tool
        String currentHumidity() {
            executedTools.add("currentHumidity");
            return "69";
        }
    }

    @Test
    void should_execute_mixed_async_and_sync_tools_in_one_response() throws Exception {

        ToolExecutionRequest temperatureRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ToolExecutionRequest humidityRequest = ToolExecutionRequest.builder()
                .id("2")
                .name("currentHumidity")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(temperatureRequest, humidityRequest), AiMessage.from("42 degrees, 69 percent"));
        MixedTools tools = new MixedTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .executeToolsConcurrently()
                .build();

        String answer = assistant.chat("What is the weather?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees, 69 percent");
        assertThat(tools.executedTools).containsExactlyInAnyOrder("currentTemperature", "currentHumidity");
        // both results are delivered, regardless of which tool was async (currentTemperature) vs sync (currentHumidity)
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .extracting(message -> ((ToolExecutionResultMessage) message).text())
                .containsExactlyInAnyOrder("42", "69");
    }

    static class SequentialAsyncTools {

        final List<String> executedTools = synchronizedList(new ArrayList<>());

        @Tool
        CompletableFuture<String> currentTemperature() {
            executedTools.add("currentTemperature");
            return CompletableFuture.supplyAsync(() -> "42", CompletableFuture.delayedExecutor(20, MILLISECONDS));
        }

        @Tool
        CompletableFuture<String> currentHumidity() {
            executedTools.add("currentHumidity");
            return CompletableFuture.supplyAsync(() -> "69", CompletableFuture.delayedExecutor(20, MILLISECONDS));
        }
    }

    @Test
    void should_execute_multiple_async_tools_sequentially_in_one_response() throws Exception {

        ToolExecutionRequest temperatureRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ToolExecutionRequest humidityRequest = ToolExecutionRequest.builder()
                .id("2")
                .name("currentHumidity")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(temperatureRequest, humidityRequest), AiMessage.from("42 degrees, 69 percent"));
        SequentialAsyncTools tools = new SequentialAsyncTools();

        // executeToolsConcurrently(false): opt out of the async path's concurrent-by-default tool execution,
        // so the loop chains the async tools (thenCompose), one after another
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .executeToolsConcurrently(false)
                .build();

        String answer = assistant.chat("What is the weather?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees, 69 percent");
        // the chained path initiates the second tool only after the first one's future completes, so the
        // invocation order is deterministic and matches the request order
        assertThat(tools.executedTools).containsExactly("currentTemperature", "currentHumidity");
        // both results are delivered, in request order
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .extracting(message -> ((ToolExecutionResultMessage) message).text())
                .containsExactly("42", "69");
    }

    @Test
    void should_execute_async_tools_in_multiple_rounds() throws Exception {

        ToolExecutionRequest firstRoundRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ToolExecutionRequest secondRoundRequest = ToolExecutionRequest.builder()
                .id("2")
                .name("currentHumidity")
                .arguments("{}")
                .build();
        // each round the LLM calls a single async tool, then answers
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(firstRoundRequest),
                AiMessage.from(secondRoundRequest),
                AiMessage.from("42 degrees, 69 percent"));
        SequentialAsyncTools tools = new SequentialAsyncTools();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .build();

        String answer = assistant.chat("What is the weather?").get(10, SECONDS);

        assertThat(answer).isEqualTo("42 degrees, 69 percent");
        assertThat(tools.executedTools).containsExactly("currentTemperature", "currentHumidity");
        assertThat(chatModel.requests()).hasSize(3);
        assertThat(chatModel.requests().get(0).messages()).hasSize(1);
        assertThat(chatModel.requests().get(1).messages()).hasSize(3);
        assertThat(chatModel.requests().get(2).messages()).hasSize(5);
    }

    static class PartiallyFailingAsyncTools {

        @Tool
        CompletableFuture<String> currentTemperature() {
            return CompletableFuture.failedFuture(new RuntimeException("Temperature service is unavailable"));
        }

        @Tool
        CompletableFuture<String> currentHumidity() {
            return CompletableFuture.supplyAsync(() -> "69", CompletableFuture.delayedExecutor(20, MILLISECONDS));
        }
    }

    @Test
    void should_handle_one_async_tool_failing_and_one_succeeding_in_parallel() throws Exception {

        ToolExecutionRequest temperatureRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ToolExecutionRequest humidityRequest = ToolExecutionRequest.builder()
                .id("2")
                .name("currentHumidity")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(temperatureRequest, humidityRequest), AiMessage.from("It is 69 percent humidity"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new PartiallyFailingAsyncTools())
                .executeToolsConcurrently()
                .build();

        String answer = assistant.chat("What is the weather?").get(10, SECONDS);

        assertThat(answer).isEqualTo("It is 69 percent humidity");
        assertThat(chatModel.requests()).hasSize(2);

        // both results reach the LLM, in request order: the failed tool's error message (default non-throwing
        // handler) followed by the successful tool's value. Order is preserved regardless of completion order.
        List<ToolExecutionResultMessage> toolResults = chatModel.requests().get(1).messages().stream()
                .filter(message -> message instanceof ToolExecutionResultMessage)
                .map(message -> (ToolExecutionResultMessage) message)
                .toList();
        assertThat(toolResults).hasSize(2);
        assertThat(toolResults.get(0).text()).isEqualTo("Temperature service is unavailable");
        assertThat(toolResults.get(0).isError()).isTrue();
        assertThat(toolResults.get(1).text()).isEqualTo("69");
        assertThat(toolResults.get(1).isError()).isFalse();
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (BrokenBarrierException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    interface BlockingAssistant {

        String chat(String userMessage);
    }

    @Test
    void should_join_tool_returning_completable_future_in_blocking_ai_service() {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees"));
        AsyncTools tools = new AsyncTools();

        BlockingAssistant assistant = AiServices.builder(BlockingAssistant.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();

        assertThat(assistant.chat("What is the temperature?")).isEqualTo("It is 42 degrees");
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> assertThat(((ToolExecutionResultMessage) message).text())
                        .isEqualTo("42"));
    }

    @Test
    void should_execute_custom_tool_executor_overriding_executeAsync() throws Exception {

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("currentTemperature")
                .build();
        ToolExecutor toolExecutor = new ToolExecutor() {

            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                throw new AssertionError("Blocking execute() must not be called "
                        + "when the AI Service method returns a CompletableFuture");
            }

            @Override
            public CompletableFuture<ToolExecutionResult> executeAsync(
                    ToolExecutionRequest request, InvocationContext context) {
                return CompletableFuture.supplyAsync(
                        () -> ToolExecutionResult.builder().resultText("42").build(),
                        CompletableFuture.delayedExecutor(50, MILLISECONDS));
            }
        };
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("currentTemperature")
                        .arguments("{}")
                        .build()),
                AiMessage.from("It is 42 degrees"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(Map.of(toolSpecification, toolExecutor))
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        assertThat(future.get(10, SECONDS)).isEqualTo("It is 42 degrees");
        assertThat(chatModel.requests()).hasSize(2);
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> assertThat(((ToolExecutionResultMessage) message).text())
                        .isEqualTo("42"));
    }

    @Test
    void should_fail_future_when_custom_tool_executor_does_not_support_async_execution() {

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("currentTemperature")
                .build();
        ToolExecutor syncOnlyToolExecutor = (request, memoryId) -> "42";

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("currentTemperature")
                        .arguments("{}")
                        .build()),
                AiMessage.from("It is 42 degrees"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(Map.of(toolSpecification, syncOnlyToolExecutor))
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        assertThatThrownBy(() -> future.get(10, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .rootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support asynchronous execution")
                .hasMessageContaining("executeAsync");

        // a configuration gap, not a tool failure: it must fail the invocation, not be sent to the LLM
        assertThat(chatModel.requests()).hasSize(1);
    }

    static class FailingAsyncTools {

        @Tool
        CompletableFuture<String> currentTemperature() {
            return CompletableFuture.failedFuture(new RuntimeException("Weather service is unavailable"));
        }
    }

    @Test
    void should_send_error_to_LLM_when_tool_future_fails() throws Exception {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest), AiMessage.from("I was not able to get the temperature"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new FailingAsyncTools())
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        assertThat(future.get(10, SECONDS)).isEqualTo("I was not able to get the temperature");
        assertThat(chatModel.requests()).hasSize(2);
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> {
                    ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) message;
                    assertThat(toolResult.text()).isEqualTo("Weather service is unavailable");
                    assertThat(toolResult.isError()).isTrue();
                });
    }

    static class SynchronouslyThrowingAsyncTools {

        @Tool
        CompletableFuture<String> currentTemperature() {
            // throws synchronously (e.g. argument validation) before constructing the future - hits a
            // different branch (InvocationTargetException) than a returned failed future
            throw new IllegalArgumentException("Invalid city");
        }
    }

    @Test
    void should_send_error_to_LLM_when_async_tool_throws_synchronously() throws Exception {

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest), AiMessage.from("I was not able to get the temperature"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(new SynchronouslyThrowingAsyncTools())
                .build();

        CompletableFuture<String> future = assistant.chat("What is the temperature?");

        // a synchronous throw from a future-returning tool routes to the error handler identically to a
        // returned failed future: the error message is sent to the LLM and the loop reprocesses
        assertThat(future.get(10, SECONDS)).isEqualTo("I was not able to get the temperature");
        assertThat(chatModel.requests()).hasSize(2);
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> {
                    ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) message;
                    assertThat(toolResult.text()).isEqualTo("Invalid city");
                    assertThat(toolResult.isError()).isTrue();
                });
    }

    interface AssistantReturningRawFuture {

        @SuppressWarnings("rawtypes")
        CompletableFuture chat(String userMessage);
    }

    @Test
    void should_fail_when_completable_future_is_not_parameterized() {

        assertThatThrownBy(() -> AiServices.builder(AssistantReturningRawFuture.class)
                        .chatModel(ChatModelMock.thatAlwaysResponds("Berlin"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be parameterized with a concrete type");
    }

    interface AssistantReturningWildcardFuture {

        CompletableFuture<?> chat(String userMessage);
    }

    @Test
    void should_fail_when_completable_future_is_parameterized_with_wildcard() {

        assertThatThrownBy(() -> AiServices.builder(AssistantReturningWildcardFuture.class)
                        .chatModel(ChatModelMock.thatAlwaysResponds("Berlin"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be parameterized with a concrete type");
    }
}
