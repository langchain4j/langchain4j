package dev.langchain4j.http.client.sse;

import dev.langchain4j.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Internal
public class ServerSentEventListenerUtils {

    private static final Logger log = LoggerFactory.getLogger(ServerSentEventListenerUtils.class);

    public static void ignoringExceptions(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.warn(
                    "An exception occurred during the invocation of the SSE listener. "
                            + "This exception has been ignored.",
                    e);
        }
    }
}
