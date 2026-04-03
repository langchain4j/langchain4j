package dev.langchain4j.http.client.sse;

import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * @since 1.8.0
 */
@Internal
public class ServerSentEventParsingHandleUtils {

    public static StreamingHandle toStreamingHandle(ServerSentEventParsingHandle parsingHandle) {
        return new StreamingHandle() {

            @Override
            public void cancel() {
                parsingHandle.cancel();
            }

            @Override
            public boolean isCancelled() {
                return parsingHandle.isCancelled();
            }
        };
    }
}
