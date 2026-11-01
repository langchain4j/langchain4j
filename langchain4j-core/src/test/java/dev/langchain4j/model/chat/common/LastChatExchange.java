package dev.langchain4j.model.chat.common;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

/**
 * Remembers the last {@link ChatRequest} that was sent to the LLM and the {@link ChatResponse} that it returned,
 * and appends them to the failure message when a test fails.
 * Otherwise, one has to find the response in the logs, provided that logging is enabled at all
 * and that the logs of the tests running in parallel can be told apart.
 * <p>
 * It is registered on {@link AbstractBaseChatModelIT}, so all tests that inherit from it are covered.
 * Tests that call the LLM on their own (for example, by overriding
 * {@link AbstractBaseChatModelIT#chat(Object, ChatRequest)}) have to call
 * {@link #recordRequest(ChatRequest)} and {@link #recordResponse(ChatResponse)} themselves.
 */
public class LastChatExchange implements BeforeEachCallback, TestExecutionExceptionHandler {

    // requests can be huge, for example when they contain images encoded into Base64
    private static final int MAX_LENGTH = 10_000;

    private static final ThreadLocal<ChatRequest> LAST_REQUEST = new ThreadLocal<>();
    private static final ThreadLocal<ChatResponse> LAST_RESPONSE = new ThreadLocal<>();

    public static void recordRequest(ChatRequest chatRequest) {
        LAST_REQUEST.set(chatRequest);
        LAST_RESPONSE.remove(); // the response to this request is not known yet
    }

    public static void recordResponse(ChatResponse chatResponse) {
        LAST_RESPONSE.set(chatResponse);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        LAST_REQUEST.remove();
        LAST_RESPONSE.remove();
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {

        ChatRequest lastRequest = LAST_REQUEST.get();
        if (lastRequest == null) {
            throw throwable;
        }

        ChatResponse lastResponse = LAST_RESPONSE.get();

        // the type of the original failure is not lost, it is reported as the cause
        String failure = isNullOrBlank(throwable.getMessage()) ? throwable.toString() : throwable.getMessage();

        String message = failure + "\n\nLast chat request:\n" + truncate(lastRequest) + "\n\nLast chat response:\n"
                + (lastResponse == null ? "none, the LLM call did not return a response" : truncate(lastResponse));

        throw new AssertionError(message, throwable);
    }

    private static String truncate(Object object) {
        String string = String.valueOf(object);
        if (string.length() <= MAX_LENGTH) {
            return string;
        }
        return string.substring(0, MAX_LENGTH) + "... (truncated)";
    }
}
