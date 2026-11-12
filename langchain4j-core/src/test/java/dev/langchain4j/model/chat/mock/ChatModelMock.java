package dev.langchain4j.model.chat.mock;

import static dev.langchain4j.internal.ExceptionMapper.mappingException;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.RetryUtils.retryPolicyBuilder;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedList;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * An implementation of a {@link ChatModel} useful for unit testing.
 * Always returns a static response and records all invocations for verification at the end of a test.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 */
@Experimental
public class ChatModelMock implements ChatModel {

    private final String staticResponse;
    private final RuntimeException exception;
    private final Function<ChatRequest, AiMessage> aiMessageGenerator;
    private final List<ChatRequest> requests = synchronizedList(new ArrayList<>());

    private static final RetryUtils.RetryPolicy DEFAULT_NO_RETRY_POLICY =
            retryPolicyBuilder().maxRetries(0).build();
    private RetryUtils.RetryPolicy retryPolicy = DEFAULT_NO_RETRY_POLICY;
    private Set<Capability> supportedCapabilities = Set.of();
    private List<ChatModelListener> listeners = List.of();
    private Queue<String> modelNames = new ConcurrentLinkedQueue<>();
    private List<TokenUsage> tokenUsages = List.of();
    private FinishReason finishReason;

    public ChatModelMock(String staticResponse) {
        this.staticResponse = ensureNotBlank(staticResponse, "staticResponse");
        this.exception = null;
        this.aiMessageGenerator = null;
    }

    public ChatModelMock(RuntimeException exception) {
        this.staticResponse = null;
        this.exception = ensureNotNull(exception, "exception");
        this.aiMessageGenerator = null;
    }

    public ChatModelMock(Function<ChatRequest, AiMessage> aiMessageGenerator) {
        this.staticResponse = null;
        this.exception = null;
        this.aiMessageGenerator = ensureNotNull(aiMessageGenerator, "aiMessageGenerator");
    }

    public ChatModelMock withRetryPolicy(RetryUtils.RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
        return this;
    }

    public ChatModelMock withSupportedCapabilities(Capability... supportedCapabilities) {
        this.supportedCapabilities = Set.of(supportedCapabilities);
        return this;
    }

    /**
     * Sets the model name reported in the response metadata, one name per call, in the given order.
     * Once all names are used, the model name is {@code null}.
     */
    public ChatModelMock withModelNames(String... modelNames) {
        this.modelNames = new ConcurrentLinkedQueue<>(asList(modelNames));
        return this;
    }

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
     * Reports the given token usages in successive responses, repeating the last one.
     */
    public ChatModelMock withTokenUsages(TokenUsage... tokenUsages) {
        this.tokenUsages = List.of(tokenUsages);
        return this;
    }

    private TokenUsage recordRequest(ChatRequest chatRequest) {
        synchronized (requests) {
            requests.add(chatRequest);
            return tokenUsages.isEmpty() ? null : tokenUsages.get(Math.min(requests.size(), tokenUsages.size()) - 1);
        }
    }

    /**
     * Reports the given finish reason in every response.
     */
    public ChatModelMock withFinishReason(FinishReason finishReason) {
        this.finishReason = finishReason;
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
        TokenUsage tokenUsage = recordRequest(chatRequest);
        return respond(chatRequest, tokenUsage);
    }

    /**
     * Completes off the calling thread with the same configured response as {@link #doChat(ChatRequest)}.
     * Does not delegate to {@link #doChat(ChatRequest)}, so a Mockito spy can verify
     * that the blocking path is never invoked.
     */
    @Override
    public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
        TokenUsage tokenUsage = recordRequest(chatRequest);
        return CompletableFuture.supplyAsync(() -> respond(chatRequest, tokenUsage));
    }

    private ChatResponse respond(ChatRequest chatRequest, TokenUsage tokenUsage) {
        if (exception != null) {
            throw exception;
        }

        AiMessage aiMessage = retryPolicy.withRetry(() -> mappingException(() -> getAiMessage(chatRequest)));

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder()
                        .modelName(modelNames.poll())
                        .tokenUsage(tokenUsage)
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private AiMessage getAiMessage(ChatRequest chatRequest) {
        return aiMessageGenerator != null ? aiMessageGenerator.apply(chatRequest) : AiMessage.from(staticResponse);
    }

    public String userMessageText() {
        if (requests.size() != 1) {
            throw runtime("Expected exactly 1 request, got: " + requests.size());
        }

        List<ChatMessage> messages = requests.get(0).messages();
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
     * @deprecated use {@link #requests()} instead
     */
    @Deprecated(since = "1.12.0")
    public List<List<ChatMessage>> getRequests() {
        return requests.stream().map(ChatRequest::messages).toList();
    }

    public List<ChatRequest> requests() {
        return requests;
    }

    public ChatRequest request() {
        if (requests.size() == 1) {
            return requests.get(0);
        } else {
            throw runtime("Expected exactly 1 chat request, got: " + requests.size());
        }
    }

    public static ChatModelMock thatAlwaysResponds(String response) {
        return new ChatModelMock(response);
    }

    /**
     * Responds with the given message to every request. Fails on the second request if the message contains tool
     * execution requests, because repeating it would make an AI Service call the tools again and again.
     */
    public static ChatModelMock thatAlwaysResponds(AiMessage aiMessage) {
        return thatAlwaysResponds(new AiMessage[] {aiMessage});
    }

    /**
     * Responds with the given messages in order, one per request. Once all of them were given, the last one is given
     * again for every further request (like Mockito's {@code thenReturn(a, b)}), unless it contains tool execution
     * requests: then the mock fails, because repeating it would make an AI Service call the tools again and again.
     */
    public static ChatModelMock thatAlwaysResponds(AiMessage... aiMessages) {
        ScriptedResponses responses = new ScriptedResponses("ChatModelMock", asList(aiMessages));
        return new ChatModelMock(ignored -> responses.next());
    }

    public static ChatModelMock thatResponds(Function<ChatRequest, AiMessage> aiMessageGenerator) {
        return new ChatModelMock(aiMessageGenerator);
    }

    public static ChatModelMock thatAlwaysThrowsException() {
        return thatAlwaysThrowsExceptionWithMessage("Something went wrong, but this is an expected exception");
    }

    public static ChatModelMock thatAlwaysThrowsExceptionWithMessage(String message) {
        return new ChatModelMock(new RuntimeException(message));
    }
}
