package dev.langchain4j.model.chat.mock;

import static dev.langchain4j.internal.ExceptionMapper.mappingException;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.RetryUtils.retryPolicyBuilder;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.synchronizedList;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.ArrayList;
import java.util.List;
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
    private final List<List<ChatMessage>> requests = synchronizedList(new ArrayList<>());

    private static final RetryUtils.RetryPolicy DEFAULT_NO_RETRY_POLICY = retryPolicyBuilder().maxRetries(0).build();
    private RetryUtils.RetryPolicy retryPolicy = DEFAULT_NO_RETRY_POLICY;

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

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        requests.add(new ArrayList<>(chatRequest.messages()));

        if (exception != null) {
            throw exception;
        }

        AiMessage aiMessage = retryPolicy.withRetry(() -> mappingException(() -> getAiMessage(chatRequest)));

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder().build())
                .build();
    }

    private AiMessage getAiMessage(ChatRequest chatRequest) {
        return aiMessageGenerator != null ? aiMessageGenerator.apply(chatRequest) : AiMessage.from(staticResponse);
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
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else {
            throw runtime("Expected UserMessage, got: " + message);
        }
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
}
