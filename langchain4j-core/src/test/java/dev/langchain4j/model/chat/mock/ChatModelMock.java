package dev.langchain4j.model.chat.mock;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.model.chat.policy.PolicyUtil.invokePolicy;
import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.synchronizedList;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.policy.InvocationPolicy;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * An implementation of a {@link ChatLanguageModel} useful for unit testing.
 * Always returns a static response and records all invocations for verification at the end of a test.
 * This implementation is experimental and subject to change in the future. It may utilize Mockito internally.
 */
public class ChatModelMock implements ChatLanguageModel {

    private final String staticResponse;
    private final RuntimeException exception;
    private final Function<ChatRequest, AiMessage> aiMessageGenerator;
    private final List<List<ChatMessage>> requests = synchronizedList(new ArrayList<>());

    private InvocationPolicy invocationPolicy = EmptyInvocationPolicy.INSTANCE;

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

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        requests.add(new ArrayList<>(chatRequest.messages()));

        if (exception != null) {
            throw exception;
        }

        AiMessage aiMessage = aiMessageGenerator != null ? internalChat(chatRequest) : AiMessage.from(staticResponse);

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder().build())
                .build();
    }

    private AiMessage internalChat(ChatRequest chatRequest) {
        return invokePolicy(invocationPolicy, () -> aiMessageGenerator.apply(chatRequest));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        requests.add(new ArrayList<>(messages));

        if (exception != null) {
            throw exception;
        }

        return Response.from(AiMessage.from(staticResponse));
    }

    public String userMessageText() {
        if (requests.size() != 1) {
            throw runtime("Expected exactly 1 request, got: " + requests.size());
        }

        List<ChatMessage> messages = requests.get(0);
        if (messages.size() != 1) {
            throw runtime("Expected exactly 1 message, got: " + messages.size());
        }

        ChatMessage message = messages.get(0);
        if (!(message instanceof UserMessage)) {
            throw runtime("Expected exactly UserMessage, got: " + message);
        }

        return message.text();
    }

    public ChatModelMock withInvocationPolicy(InvocationPolicy invocationPolicy) {
        this.invocationPolicy = invocationPolicy;
        return this;
    }

    public static ChatModelMock thatAlwaysResponds(String response) {
        return new ChatModelMock(response);
    }

    public static ChatModelMock thatAlwaysThrowsException() {
        return thatAlwaysThrowsExceptionWithMessage("Something went wrong, but this is an expected exception");
    }

    public static ChatModelMock thatAlwaysThrowsExceptionWithMessage(String message) {
        return new ChatModelMock(new RuntimeException(message));
    }

    private static class EmptyInvocationPolicy implements InvocationPolicy {

        private static final EmptyInvocationPolicy INSTANCE = new EmptyInvocationPolicy();

        @Override
        public Callable<?> apply(final Callable<?> action) {
            return action;
        }
    }
}
