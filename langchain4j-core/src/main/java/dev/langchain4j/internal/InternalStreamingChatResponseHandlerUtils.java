package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public class InternalStreamingChatResponseHandlerUtils {

    private static final Logger log = LoggerFactory.getLogger(InternalStreamingChatResponseHandlerUtils.class);

    public static void withLoggingExceptions(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn("An exception occurred during the invocation of StreamingChatResponseHandler.onError(). "
                    + "This exception has been ignored.", e);
        }
    }

    public static void onPartialResponse(StreamingChatResponseHandler handler, String partialResponse) {
        if (isNullOrEmpty(partialResponse)) {
            return;
        }

        try {
            handler.onPartialResponse(partialResponse);
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    public static void onPartialThinking(StreamingChatResponseHandler handler, String partialThinking) {
        if (isNullOrEmpty(partialThinking)) {
            return;
        }

        try {
            handler.onPartialThinking(new PartialThinking(partialThinking));
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    public static void onPartialToolCall(StreamingChatResponseHandler handler, PartialToolCall partialToolCall) {
        try {
            handler.onPartialToolCall(partialToolCall);
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    public static void onCompleteToolCall(StreamingChatResponseHandler handler, CompleteToolCall completeToolCall) {
        try {
            handler.onCompleteToolCall(completeToolCall);
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    public static void onCompleteResponse(StreamingChatResponseHandler handler, ChatResponse completeResponse) {
        try {
            handler.onCompleteResponse(completeResponse);
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }
}
