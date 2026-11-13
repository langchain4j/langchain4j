package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

@Internal
public class InternalStreamingChatResponseHandlerUtils {

    private static final Logger log = LoggerFactory.getLogger(InternalStreamingChatResponseHandlerUtils.class);

    /**
     * Logs a failure that is not reported to a handler, because the handler already received the complete response
     * or an error.
     */
    public static void logIgnoredError(Throwable error) {
        log.warn(
                "Ignoring a failure after the StreamingChatResponseHandler already received the complete response or an error",
                error);
    }

    /**
     * Logs an exception thrown by {@link StreamingChatResponseHandler#onError(Throwable)} on another thread than the
     * one that called {@code chat(...)}, or after {@code chat(...)} returned: nobody could catch it there.
     */
    public static void logOnErrorFailure(Throwable onErrorFailure) {
        log.warn(
                "An exception occurred during the invocation of StreamingChatResponseHandler.onError() "
                        + "on another thread or after chat(...) returned. This exception has been ignored.",
                onErrorFailure);
    }

    /**
     * Runs the given notification of listeners, and logs whatever it throws, including an {@link Error}, so that a
     * failing listener does not stop the failure from reaching the handler.
     */
    public static void notifyListenersIgnoringFailures(Runnable notification) {
        try {
            notification.run();
        } catch (Throwable e) {
            Exceptions.restoreInterruptIfInterrupted(e);
            log.warn("An exception occurred while notifying the chat model listeners. This exception has been ignored.", e);
        }
    }

    public static void withLoggingExceptions(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn(
                    "An exception occurred during the invocation of StreamingChatResponseHandler.onError(). "
                            + "This exception has been ignored.",
                    e);
        }
    }

    /**
     * @deprecated Use {@link #onPartialResponse(StreamingChatResponseHandler, String, StreamingHandle)} instead.
     */
    @Deprecated(forRemoval = true, since = "1.8.0")
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

    /**
     * @since 1.8.0
     */
    public static void onPartialResponse(
            StreamingChatResponseHandler handler, String partialResponse, StreamingHandle streamingHandle) {
        if (isNullOrEmpty(partialResponse)) {
            return;
        }

        try {
            handler.onPartialResponse(
                    new PartialResponse(partialResponse), new PartialResponseContext(streamingHandle));
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    /**
     * @deprecated Use {@link #onPartialThinking(StreamingChatResponseHandler, String, StreamingHandle)} instead.
     */
    @Deprecated(forRemoval = true, since = "1.8.0")
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

    /**
     * @since 1.8.0
     */
    public static void onPartialThinking(
            StreamingChatResponseHandler handler, String partialThinking, StreamingHandle streamingHandle) {
        if (isNullOrEmpty(partialThinking)) {
            return;
        }

        try {
            handler.onPartialThinking(
                    new PartialThinking(partialThinking), new PartialThinkingContext(streamingHandle));
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    /**
     * @deprecated Use {@link #onPartialToolCall(StreamingChatResponseHandler, PartialToolCall, StreamingHandle)} instead.
     */
    @Deprecated(forRemoval = true, since = "1.8.0")
    public static void onPartialToolCall(StreamingChatResponseHandler handler, PartialToolCall partialToolCall) {
        try {
            handler.onPartialToolCall(partialToolCall);
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    /**
     * @since 1.8.0
     */
    public static void onPartialToolCall(
            StreamingChatResponseHandler handler, PartialToolCall partialToolCall, StreamingHandle streamingHandle) {
        try {
            handler.onPartialToolCall(partialToolCall, new PartialToolCallContext(streamingHandle));
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

    public static void onUnmappedRawEvent(StreamingChatResponseHandler handler, Object rawEvent) {
        try {
            handler.onUnmappedRawEvent(rawEvent);
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
