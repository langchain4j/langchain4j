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
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.chat.response.StreamingHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Flow.Subscriber;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

@Internal
public class InternalStreamingChatResponseHandlerUtils {

    private static final Logger log = LoggerFactory.getLogger(InternalStreamingChatResponseHandlerUtils.class);

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
    public static void onPartialResponse(StreamingChatResponseHandler handler,
                                         String partialResponse,
                                         StreamingHandle streamingHandle) {
        if (isNullOrEmpty(partialResponse)) {
            return;
        }

        try {
            handler.onPartialResponse(new PartialResponse(partialResponse), new PartialResponseContext(streamingHandle));
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    /**
     * @since 2.0.0
     */
    public static void onPartialResponse(Subscriber<? super StreamingEvent> subscriber, String partialResponse) {
        if (isNullOrEmpty(partialResponse)) {
            return;
        }

        try {
            subscriber.onNext(new PartialResponse(partialResponse));
        } catch (Exception e) {
            withLoggingExceptions(() -> subscriber.onError(e)); // TODO?
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
    public static void onPartialThinking(StreamingChatResponseHandler handler,
                                         String partialThinking,
                                         StreamingHandle streamingHandle) {
        if (isNullOrEmpty(partialThinking)) {
            return;
        }

        try {
            handler.onPartialThinking(new PartialThinking(partialThinking), new PartialThinkingContext(streamingHandle));
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    /**
     * @since 2.0.0
     */
    public static void onPartialThinking(Subscriber<? super StreamingEvent> subscriber, String partialThinking) {
        if (isNullOrEmpty(partialThinking)) {
            return;
        }

        try {
            subscriber.onNext(new PartialThinking(partialThinking));
        } catch (Exception e) {
            withLoggingExceptions(() -> subscriber.onError(e)); // TODO?
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
    public static void onPartialToolCall(StreamingChatResponseHandler handler,
                                         PartialToolCall partialToolCall,
                                         StreamingHandle streamingHandle) {
        try {
            handler.onPartialToolCall(partialToolCall, new PartialToolCallContext(streamingHandle));
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    /**
     * @since 1.8.0
     */
    public static void onPartialToolCall(Subscriber<? super StreamingEvent> subscriber, PartialToolCall partialToolCall) {
        try {
            subscriber.onNext(partialToolCall);
        } catch (Exception e) {
            withLoggingExceptions(() -> subscriber.onError(e)); // TODO?
        }
    }

    public static void onCompleteToolCall(StreamingChatResponseHandler handler, CompleteToolCall completeToolCall) {
        try {
            handler.onCompleteToolCall(completeToolCall);
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    public static void onCompleteToolCall(Subscriber<? super StreamingEvent> subscriber, CompleteToolCall completeToolCall) {
        try {
            subscriber.onNext(completeToolCall);
        } catch (Exception e) {
            withLoggingExceptions(() -> subscriber.onError(e)); // TODO
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
