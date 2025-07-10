package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.CompleteToolExecutionRequest;
import dev.langchain4j.agent.tool.PartialToolExecutionRequest;
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

    public static void onPartialToolExecutionRequest(StreamingChatResponseHandler handler,
                                                     PartialToolExecutionRequest request) {
        try {
            handler.onPartialToolExecutionRequest(request);
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }

    public static void onCompleteToolExecutionRequest(StreamingChatResponseHandler handler,
                                                      CompleteToolExecutionRequest request) {
        try {
            handler.onCompleteToolExecutionRequest(request);
        } catch (Exception e) {
            withLoggingExceptions(() -> handler.onError(e));
        }
    }
}
