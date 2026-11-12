package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.mock.ScriptedResponses.Step;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ChatModel} for tests: it needs no network, answers the way the test tells it to, and records the requests
 * it receives.
 * <p>
 * Script the answers step by step, one step per request. For example, a model that first calls a tool and then answers
 * with text:
 * <pre>{@code
 * MockChatModel model = MockChatModel.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
 *         .thenResponds("Booking MS-777 is cancelled.");
 *
 * Assistant assistant = AiServices.builder(Assistant.class)
 *         .chatModel(model)
 *         .tools(new BookingTools())
 *         .build();
 * }</pre>
 * Every {@code that...} method creates a mock with its first step (one step per message for the message-list forms),
 * and every {@code then...} method adds a step. Each step answers exactly one request. A request after the last step
 * fails with an {@link IllegalStateException} that shows the request, so a test notices when the code calls the model
 * more often than the script expects, unless the code under test handles the error itself. {@code chat(...)} throws
 * it, and {@code chatAsync(...)} returns a future that fails with it.
 * <p>
 * To answer every further request with the last step, end the script with {@link #thenRepeats()}. For example, a model
 * that gives the same answer to every request:
 * <pre>{@code
 * MockChatModel model = MockChatModel.thatResponds("Hello!").thenRepeats();
 * }</pre>
 * A step can compute its answer from the request with {@link #thatResponds(Function)}, for example to answer
 * concurrent requests by their content: {@code thatResponds(request -> ...).thenRepeats()}.
 * <p>
 * To check what the model received, use {@link #request()} or {@link #requests()}, for example with
 * {@code LangChain4jAssertions.assertThat(model.request()).hasLastUserMessageContaining("MS-777")}. To check what an AI
 * Service did with the answers, capture the invocation with {@code AiServiceCapture}.
 * <p>
 * A response reports what a provider reports: the model name of the request, and the finish reason
 * {@link FinishReason#TOOL_EXECUTION} for an answer with tool calls, otherwise {@link FinishReason#STOP}. To report
 * token usage in every response, use {@link #withTokenUsage(TokenUsage)}. To report anything else, for example
 * {@link FinishReason#LENGTH} or a different token usage per step, give the whole response:
 * {@link #thatResponds(ChatResponse)}.
 * <p>
 * The mock supports no capabilities, so an AI Service that returns a structured type, for example a POJO, adds format
 * instructions to the user message instead of asking for a JSON schema response format. To test the response-format
 * path, stub {@code supportedCapabilities()} on a Mockito spy of the mock.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test. A mock
 * shared between tests, for example as a bean, fails with a clear message as soon as its script runs out, unless the
 * script ends with {@link #thenRepeats()}. The steps are taken in the order the requests arrive. A mock keeps every
 * request it receives, so it is not meant for load tests or other long runs.
 */
@Experimental
public class MockChatModel implements ChatModel {

    private static final String MOCK_NAME = "MockChatModel";

    private final ScriptedResponses script;
    private final RecordedRequests<ChatRequest> requests = new RecordedRequests<>("chat");
    private List<ChatModelListener> listeners = List.of();

    MockChatModel(List<Step> steps) {
        this.script = new ScriptedResponses(MOCK_NAME, steps);
    }

    /**
     * Adds a step: the next request is answered with the given text.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockChatModel thenResponds(String text) {
        return thenResponds(AiMessage.from(text));
    }

    /**
     * Adds a step: the next request is answered with the given message.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockChatModel thenResponds(AiMessage aiMessage) {
        script.add(Step.responding(aiMessage));
        return this;
    }

    /**
     * Adds a step: the next request is answered with exactly the given response, including its metadata, for example
     * its token usage or finish reason. Only tool calls without an id get the next id of the mock, see
     * {@link #toolCall(String, Map)}.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockChatModel thenResponds(ChatResponse chatResponse) {
        script.add(Step.responding(chatResponse));
        return this;
    }

    /**
     * Adds a step: the next request is answered with a call of the given tool.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockChatModel thenCallsTool(String toolName, Map<String, ?> arguments) {
        return thenCallsTool(toolName, toJson(arguments));
    }

    /**
     * Adds a step: the next request is answered with a call of the given tool. Use it for arguments that cannot be
     * given as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockChatModel thenCallsTool(String toolName, String jsonArguments) {
        script.add(Step.callingTool(toolName, jsonArguments));
        return this;
    }

    /**
     * Adds a step: the next request fails with exactly the given exception.
     *
     * @throws IllegalStateException if the script already ends with {@link #thenRepeats()}
     */
    public MockChatModel thenFails(RuntimeException exception) {
        script.add(Step.failing(exception));
        return this;
    }

    /**
     * Answers every further request with the last step of the script. Only the last step repeats; the script does not
     * start over. A last step that computes its answer computes it again for each request. Without it, a request after
     * the last step fails. It must be the last call of the script.
     * <p>
     * A step that calls a tool cannot repeat. To test a model that keeps calling a tool, for example the limit set with
     * {@code maxToolCallingRoundTrips}, compute the answer:
     * {@code thatResponds(request -> AiMessage.from(toolCall("getWeather", Map.of()))).thenRepeats()}.
     *
     * @throws IllegalStateException if the last step calls a tool, because an AI Service would then call the tool again
     *                               and again, or if the script already ends with {@code thenRepeats()}
     */
    public MockChatModel thenRepeats() {
        script.repeatLastStep();
        return this;
    }

    /**
     * Reports the given token usage in the response to every request, except for a step that gives the whole response:
     * that one reports exactly its own token usage. For different token usages per step, give the whole responses, see
     * {@link #thenResponds(ChatResponse)}.
     *
     * @param tokenUsage the token usage, or {@code null} for no token usage, which is also the default
     */
    public MockChatModel withTokenUsage(@Nullable TokenUsage tokenUsage) {
        script.defaultTokenUsage(tokenUsage);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockChatModel withListeners(ChatModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public MockChatModel withListeners(List<ChatModelListener> listeners) {
        this.listeners = List.copyOf(listeners);
        return this;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        Step step = script.take(requests, chatRequest);
        return script.answer(step, chatRequest).responseOrThrow();
    }

    /**
     * Answers like {@link #doChat(ChatRequest)}, with the next step of the script, and completes the future on another
     * thread, where a step that computes its answer also computes it. It does not call {@link #doChat(ChatRequest)}.
     */
    @Override
    public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
        Step step;
        try {
            step = script.take(requests, chatRequest);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.supplyAsync(
                () -> script.answer(step, chatRequest).responseOrThrow(), MockThreads.newThreadPerTask(MOCK_NAME));
    }

    /**
     * A copy of the requests received so far, in the order they arrived.
     */
    public List<ChatRequest> requests() {
        return requests.all();
    }

    /**
     * The only request received so far. To check it, for example:
     * {@code LangChain4jAssertions.assertThat(model.request()).hasLastUserMessageContaining("MS-777")}.
     *
     * @throws IllegalStateException if the mock received no request or several requests
     */
    public ChatRequest request() {
        return requests.only();
    }

    /**
     * The last request received so far. For example, the request that follows the tool results.
     *
     * @throws IllegalStateException if the mock received no request
     */
    public ChatRequest lastRequest() {
        return requests.last();
    }

    /**
     * A model that answers the first request with the given text. Add the next steps with {@code then...} methods, or
     * answer every request with this text: {@code thatResponds("Hello!").thenRepeats()}.
     */
    public static MockChatModel thatResponds(String text) {
        return thatResponds(AiMessage.from(text));
    }

    /**
     * A model that answers with the given messages in order, one per request. Add further steps with {@code then...}
     * methods. Use it for answers that {@link #thatCallsTool(String, Map)} cannot express, for example several tool
     * calls in one answer: {@code AiMessage.from(toolCall("a", argsA), toolCall("b", argsB))}.
     */
    public static MockChatModel thatResponds(AiMessage first, AiMessage... next) {
        return thatResponds(Stream.concat(Stream.of(first), Stream.of(next)).toList());
    }

    /**
     * A model that answers with the given messages in order, one per request. Add further steps with {@code then...}
     * methods.
     */
    public static MockChatModel thatResponds(List<AiMessage> aiMessages) {
        ensureNotEmpty(aiMessages, "aiMessages");
        return new MockChatModel(aiMessages.stream().map(Step::responding).toList());
    }

    /**
     * A model that answers the first request with exactly the given response, including its metadata, for example its
     * token usage, model name or finish reason. Only tool calls without an id get the next id of the mock, see
     * {@link #toolCall(String, Map)}. Add the next steps with {@code then...} methods.
     */
    public static MockChatModel thatResponds(ChatResponse chatResponse) {
        return new MockChatModel(List.of(Step.responding(chatResponse)));
    }

    /**
     * A model that computes the answer to the first request from the request. Add the next steps with {@code then...}
     * methods, or compute the answer to every request with {@code thenRepeats()}. For example, a model that answers by
     * the user's question:
     * <pre>{@code
     * MockChatModel model = MockChatModel.thatResponds(request -> {
     *     String question = UserMessage.findLast(request.messages()).orElseThrow().singleText();
     *     return AiMessage.from(question.contains("refund") ? "Refunds take 5 days." : "How can I help?");
     * }).thenRepeats();
     * }</pre>
     * If the function throws, the request fails with that exception, unchanged. With {@code chatAsync(...)}, the
     * function runs on another thread.
     */
    public static MockChatModel thatResponds(Function<ChatRequest, AiMessage> answers) {
        return new MockChatModel(List.of(Step.computing(answers)));
    }

    /**
     * A model that answers the first request with a call of the given tool. Add the next steps with {@code then...}
     * methods. The call gets the next id of the mock, see {@link #toolCall(String, Map)}.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     */
    public static MockChatModel thatCallsTool(String toolName, Map<String, ?> arguments) {
        return thatCallsTool(toolName, toJson(arguments));
    }

    /**
     * A model that answers the first request with a call of the given tool. Add the next steps with {@code then...}
     * methods. Use it for arguments that cannot be given as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     */
    public static MockChatModel thatCallsTool(String toolName, String jsonArguments) {
        return new MockChatModel(List.of(Step.callingTool(toolName, jsonArguments)));
    }

    /**
     * A model whose first request fails with exactly the given exception. To let every request fail, add
     * {@link #thenRepeats()}. To let later requests succeed, for example to test your own retry logic, add steps with
     * {@code then...} methods.
     */
    public static MockChatModel thatFails(RuntimeException exception) {
        return new MockChatModel(List.of(Step.failing(exception)));
    }

    /**
     * A call of the given tool without an id, to build an answer with {@code AiMessage.from(...)}, for example in
     * {@link #thatResponds(Function)} or for several tool calls in one answer. When a mock answers with it, the call
     * gets the next id of that mock: {@code tool_call_1}, {@code tool_call_2}, and so on, so that every call has its own
     * id, also two calls of the same tool with the same arguments. To give a call a fixed id, use
     * {@code ToolExecutionRequest.builder()}. An answer with two calls that have the same fixed id is rejected, because
     * a provider never gives two calls of one answer the same id. The ids are unique within one mock only: when several
     * mocks answer into one chat memory, for example two AI Services that share it, give the calls fixed ids.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     */
    public static ToolExecutionRequest toolCall(String toolName, Map<String, ?> arguments) {
        return toolCall(toolName, toJson(arguments));
    }

    /**
     * A call of the given tool without an id, like {@link #toolCall(String, Map)}, for arguments that cannot be given
     * as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     */
    public static ToolExecutionRequest toolCall(String toolName, String jsonArguments) {
        ensureNotBlank(toolName, "toolName");
        ensureNotNull(jsonArguments, "arguments");
        return ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(jsonArguments)
                .build();
    }

    static String toJson(Map<String, ?> arguments) {
        return Json.toJson(ensureNotNull(arguments, "arguments"));
    }
}
