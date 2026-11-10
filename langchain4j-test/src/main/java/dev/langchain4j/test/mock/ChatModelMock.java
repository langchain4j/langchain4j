package dev.langchain4j.test.mock;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.test.mock.RecordedRequests.forRequest;

import dev.langchain4j.Experimental;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.mock.ScriptedResponses.Step;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A {@link ChatModel} for tests: it needs no network, answers the way the test tells it to, and records the requests
 * it receives.
 * <p>
 * Script the answers step by step, one step per request. For example, a model that first calls a tool and then answers
 * with text:
 * <pre>{@code
 * ChatModelMock model = ChatModelMock.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
 *         .thenResponds("Booking MS-777 is cancelled.");
 *
 * Assistant assistant = AiServices.builder(Assistant.class)
 *         .chatModel(model)
 *         .tools(new BookingTools())
 *         .build();
 * }</pre>
 * Steps can be added to a mock created with {@link #thatResponds(String)},
 * {@link #thatResponds(AiMessage, AiMessage...)}, {@link #thatResponds(List)},
 * {@link #thatCallsTool(String, Map)} or {@link #thatThrows(RuntimeException)}. Once all steps were taken, the last one
 * is repeated for every further request (like Mockito's {@code thenReturn(a, b)}), unless it is a response with tool
 * calls: then the mock fails with an {@link IllegalStateException}, because repeating it would make an AI Service call
 * the tools again and again.
 * <p>
 * A model that gives the same answer to every request therefore needs only one step:
 * {@code ChatModelMock.thatResponds("Hello!")}. To compute the answer from the request, use
 * {@link #thatResponds(Function)}.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test.
 */
@Experimental
public class ChatModelMock implements ChatModel {

    private static final String MOCK_NAME = "ChatModelMock";

    private final Function<ChatRequest, AiMessage> answers;
    private final ScriptedResponses script;
    private final RecordedRequests<ChatRequest> requests = new RecordedRequests<>("chat");

    private ModelProvider provider = ModelProvider.OTHER;
    private Set<Capability> supportedCapabilities = Set.of();
    private List<ChatModelListener> listeners = List.of();
    private List<String> modelNames = List.of();
    private List<TokenUsage> tokenUsages = List.of();
    private List<FinishReason> finishReasons = List.of();

    /**
     * Creates a mock that gives the same answer to every request, for subclasses. Otherwise, use
     * {@link #thatResponds(String)}.
     */
    protected ChatModelMock(String response) {
        this(null, new ScriptedResponses(MOCK_NAME, List.of(Step.responding(AiMessage.from(response)))));
    }

    private ChatModelMock(Function<ChatRequest, AiMessage> answers, ScriptedResponses script) {
        this.answers = answers;
        this.script = script;
    }

    /**
     * Adds a step: the next request is answered with the given text.
     *
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link ChatModelMock}
     */
    public ChatModelMock thenResponds(String text) {
        return thenResponds(AiMessage.from(text));
    }

    /**
     * Adds a step: the next request is answered with the given message.
     *
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link ChatModelMock}
     */
    public ChatModelMock thenResponds(AiMessage aiMessage) {
        script().add(Step.responding(aiMessage));
        return this;
    }

    /**
     * Adds a step: the next request is answered with a call of the given tool.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link ChatModelMock}
     */
    public ChatModelMock thenCallsTool(String toolName, Map<String, ?> arguments) {
        return thenCallsTool(toolName, toJson(arguments));
    }

    /**
     * Adds a step: the next request is answered with a call of the given tool. Use it for arguments that cannot be
     * given as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link ChatModelMock}
     */
    public ChatModelMock thenCallsTool(String toolName, String jsonArguments) {
        script().addToolCall(toolName, jsonArguments);
        return this;
    }

    /**
     * Adds a step: the next request fails with exactly the given exception.
     *
     * @throws IllegalStateException if steps cannot be added to this mock, see {@link ChatModelMock}
     */
    public ChatModelMock thenThrows(RuntimeException exception) {
        script().add(Step.failing(exception));
        return this;
    }

    private ScriptedResponses script() {
        if (script == null) {
            throw new IllegalStateException("Steps can only be added to a mock created with thatResponds(String),"
                    + " thatResponds(AiMessage, AiMessage...), thatResponds(List), thatCallsTool(...) or"
                    + " thatThrows(...)");
        }
        return script;
    }

    /**
     * Reports the given provider from {@link #provider()}.
     */
    public ChatModelMock withProvider(ModelProvider provider) {
        this.provider = ensureNotNull(provider, "provider");
        return this;
    }

    @Override
    public ModelProvider provider() {
        return provider;
    }

    /**
     * Reports the given capabilities from {@link #supportedCapabilities()}, for example
     * {@code Capability.RESPONSE_FORMAT_JSON_SCHEMA} for an AI Service that returns structured output.
     */
    public ChatModelMock withSupportedCapabilities(Capability... supportedCapabilities) {
        this.supportedCapabilities = Set.of(supportedCapabilities);
        return this;
    }

    /**
     * Reports the given model name in every response.
     */
    public ChatModelMock withModelName(String modelName) {
        return withModelNames(modelName);
    }

    /**
     * Reports the given model names for successive requests, repeating the last one.
     */
    public ChatModelMock withModelNames(String... modelNames) {
        this.modelNames = List.of(modelNames);
        return this;
    }

    /**
     * Notifies the given listeners of each call, like a real model does.
     */
    public ChatModelMock withListeners(ChatModelListener... listeners) {
        this.listeners = List.of(listeners);
        return this;
    }

    /**
     * Reports the given token usage in every response.
     */
    public ChatModelMock withTokenUsage(TokenUsage tokenUsage) {
        return withTokenUsages(tokenUsage);
    }

    /**
     * Reports the given token usages for successive requests, repeating the last one.
     */
    public ChatModelMock withTokenUsages(TokenUsage... tokenUsages) {
        this.tokenUsages = List.of(tokenUsages);
        return this;
    }

    /**
     * Reports the given finish reason in every response.
     */
    public ChatModelMock withFinishReason(FinishReason finishReason) {
        return withFinishReasons(finishReason);
    }

    /**
     * Reports the given finish reasons for successive requests, repeating the last one.
     */
    public ChatModelMock withFinishReasons(FinishReason... finishReasons) {
        this.finishReasons = List.of(finishReasons);
        return this;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        int requestNumber = requests.add(chatRequest);
        return respond(answerFor(chatRequest).get(), requestNumber);
    }

    /**
     * Completes off the calling thread with the same configured response as {@link #doChat(ChatRequest)}.
     * Does not delegate to {@link #doChat(ChatRequest)}, so a Mockito spy can verify
     * that the blocking path is never invoked.
     */
    @Override
    public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
        int requestNumber = requests.add(chatRequest);
        Supplier<AiMessage> answer;
        try {
            answer = answerFor(chatRequest);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletableFuture.supplyAsync(() -> respond(answer.get(), requestNumber));
    }

    private Supplier<AiMessage> answerFor(ChatRequest chatRequest) {
        if (script == null) {
            return () -> answers.apply(chatRequest);
        }
        Step step = script.next();
        return step::responseOrThrow;
    }

    private ChatResponse respond(AiMessage aiMessage, int requestNumber) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .modelName(forRequest(modelNames, requestNumber))
                        .tokenUsage(forRequest(tokenUsages, requestNumber))
                        .finishReason(forRequest(finishReasons, requestNumber))
                        .build())
                .build();
    }

    /**
     * Only for LangChain4j's own tests.
     */
    @Internal
    public String userMessageText() {
        List<ChatRequest> received = requests();
        if (received.size() != 1) {
            throw runtime("Expected exactly 1 request, got: " + received.size());
        }

        List<ChatMessage> messages = received.get(0).messages();
        if (messages.size() != 1) {
            throw runtime("Expected exactly 1 message, got: " + messages.size());
        }

        ChatMessage message = messages.get(0);
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else {
            throw runtime("Expected UserMessage, got: " + message);
        }
    }

    /**
     * A copy of the requests received so far, in the order they arrived.
     */
    public List<ChatRequest> requests() {
        return requests.all();
    }

    /**
     * The only request received so far.
     *
     * @throws IllegalStateException if the mock received no request or several requests
     */
    public ChatRequest request() {
        return requests.only();
    }

    /**
     * A model that answers every request with the given text. To answer later requests differently, add steps with
     * {@code then...} methods.
     */
    public static ChatModelMock thatResponds(String text) {
        return thatResponds(AiMessage.from(text));
    }

    /**
     * A model that answers with the given messages in order, one per request, and then repeats the last one. It fails
     * instead of repeating a message with tool calls, because that would make an AI Service call the tools again and
     * again. Add further steps with {@code then...} methods. Use it for answers that
     * {@link #thatCallsTool(String, Map)} cannot express, for example several tool calls in one answer:
     * {@code AiMessage.from(toolCall("a", argsA), toolCall("b", argsB))}.
     */
    public static ChatModelMock thatResponds(AiMessage first, AiMessage... next) {
        return thatResponds(Stream.concat(Stream.of(first), Stream.of(next)).toList());
    }

    /**
     * A model that answers with the given messages in order, one per request. Add further steps with {@code then...}
     * methods.
     */
    public static ChatModelMock thatResponds(List<AiMessage> aiMessages) {
        ensureNotEmpty(aiMessages, "aiMessages");
        return new ChatModelMock(
                null,
                new ScriptedResponses(
                        MOCK_NAME, aiMessages.stream().map(Step::responding).toList()));
    }

    /**
     * A model that computes the answer from each request.
     */
    public static ChatModelMock thatResponds(Function<ChatRequest, AiMessage> answers) {
        return new ChatModelMock(ensureNotNull(answers, "answers"), null);
    }

    /**
     * A model that answers the first request with a call of the given tool. Add the next steps with {@code then...}
     * methods. The id of the call contains the number of its step, so that calls of the same tool can be told apart.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     */
    public static ChatModelMock thatCallsTool(String toolName, Map<String, ?> arguments) {
        return thatCallsTool(toolName, toJson(arguments));
    }

    /**
     * A model that answers the first request with a call of the given tool. Add the next steps with {@code then...}
     * methods. Use it for arguments that cannot be given as a map, for example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     */
    public static ChatModelMock thatCallsTool(String toolName, String jsonArguments) {
        return new ChatModelMock(
                null, new ScriptedResponses(MOCK_NAME, List.of(Step.callingTool(toolName, jsonArguments, 1))));
    }

    /**
     * A model whose requests all fail with exactly the given exception. To let later requests succeed, for example to
     * test your own retry logic, add steps with {@code then...} methods.
     */
    public static ChatModelMock thatThrows(RuntimeException exception) {
        return new ChatModelMock(null, new ScriptedResponses(MOCK_NAME, List.of(Step.failing(exception))));
    }

    /**
     * A call of the given tool, to build an answer with {@code AiMessage.from(...)}, for example in
     * {@link #thatResponds(Function)} or for several tool calls in one answer. The id of the call is
     * {@code call_<toolName>}. For two calls of the same tool with the same arguments in one answer, set different ids
     * with {@code ToolExecutionRequest.builder()}.
     *
     * @param toolName  the name of the tool
     * @param arguments the arguments of the tool, by name
     */
    public static ToolExecutionRequest toolCall(String toolName, Map<String, ?> arguments) {
        return toolCall(toolName, toJson(arguments));
    }

    /**
     * A call of the given tool, like {@link #toolCall(String, Map)}, for arguments that cannot be given as a map, for
     * example invalid JSON.
     *
     * @param toolName      the name of the tool
     * @param jsonArguments the arguments of the tool, as JSON
     */
    public static ToolExecutionRequest toolCall(String toolName, String jsonArguments) {
        return toolCall("call_" + toolName, toolName, jsonArguments);
    }

    static ToolExecutionRequest toolCall(String id, String toolName, String jsonArguments) {
        ensureNotBlank(toolName, "toolName");
        ensureNotNull(jsonArguments, "arguments");
        return ToolExecutionRequest.builder()
                .id(id)
                .name(toolName)
                .arguments(jsonArguments)
                .build();
    }

    static String toJson(Map<String, ?> arguments) {
        return Json.toJson(ensureNotNull(arguments, "arguments"));
    }
}
