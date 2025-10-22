package dev.langchain4j.http.client.sse;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import dev.langchain4j.model.chat.response.CancellationUnsupportedStreamingHandle;
import dev.langchain4j.model.chat.response.StreamingHandle;

public class DefaultServerSentEventParser implements ServerSentEventParser {

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
        ServerSentEventParseRequest parseRequest = ServerSentEventParseRequest.builder()
                .inputStream(httpResponseBody)
                .listener(listener)
                .streamingHandle(new CancellationUnsupportedStreamingHandle())
                .build();
        parse(parseRequest);
    }

    @Override
    public void parse(ServerSentEventParseRequest parseRequest) {
        ServerSentEventListener listener = parseRequest.listener();
        StreamingHandle streamingHandle = parseRequest.streamingHandle();
        ServerSentEventContext context = new ServerSentEventContext(streamingHandle);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(parseRequest.inputStream(), UTF_8))) {

            String event = null;
            StringBuilder data = new StringBuilder();

            String line;
            while (!streamingHandle.isCancelled() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        ServerSentEvent sse = new ServerSentEvent(event, data.toString());
                        ignoringExceptions(() -> listener.onEvent(sse, context));
                        event = null;
                        data.setLength(0);
                    }
                    continue;
                }

                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    String content = line.substring("data:".length());
                    if (!data.isEmpty()) {
                        data.append("\n");
                    }
                    data.append(content.trim());
                }
            }

            if (!data.isEmpty()) {
                ServerSentEvent sse = new ServerSentEvent(event, data.toString());
                ignoringExceptions(() -> listener.onEvent(sse, context));
            }
        } catch (IOException e) {
            ignoringExceptions(() -> listener.onError(e));
        }
    }
}
