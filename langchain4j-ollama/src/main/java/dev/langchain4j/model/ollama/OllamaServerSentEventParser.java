package dev.langchain4j.model.ollama;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.Internal;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParsingHandle;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventParsingHandle;

/**
 * Ollama does not follow SSE standard for streaming, it uses newline delimited JSON format.
 * The HTTP response body from Ollama looks like this:
 * <pre>
 * {"model":"tinydolphin","created_at":"2025-01-22T11:22:42.180546Z","message":{"role":"assistant","content":" The"},"done":false}
 * {"model":"tinydolphin","created_at":"2025-01-22T11:22:42.186813Z","message":{"role":"assistant","content":" capital"},"done":false}
 * ...
 * {"model":"tinydolphin","created_at":"2025-01-22T11:22:42.223184Z","message":{"role":"assistant","content":""},"done_reason":"stop","done":true,"total_duration":181553584,"load_duration":27906292,"prompt_eval_count":36,"prompt_eval_duration":109000000,"eval_count":8,"eval_duration":42000000}
 * </pre>
 */
@Internal
class OllamaServerSentEventParser implements ServerSentEventParser {

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
        ServerSentEventParsingHandle parsingHandle = new DefaultServerSentEventParsingHandle(httpResponseBody);
        ServerSentEventContext context = new ServerSentEventContext(parsingHandle);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponseBody, UTF_8))) {
            String line;
            while (!parsingHandle.isCancelled() && (line = reader.readLine()) != null) {
                ServerSentEvent sse = new ServerSentEvent(null, line);
                ignoringExceptions(() -> listener.onEvent(sse, context));
            }
        } catch (IOException e) {
            ignoringExceptions(() -> listener.onError(e));
        }
    }

    @Override
    public Incremental incremental() {
        return new OllamaIncremental();
    }

    /** Byte-driven NDJSON parser: every {@code \n}-terminated line becomes one event. */
    private static final class OllamaIncremental implements Incremental {

        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        @Override
        public List<ServerSentEvent> feed(ByteBuffer bytes) {
            int len = bytes.remaining();
            byte[] arr = new byte[len];
            bytes.get(arr);
            pending.write(arr, 0, len);

            byte[] all = pending.toByteArray();
            List<ServerSentEvent> events = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < all.length; i++) {
                if (all[i] == '\n') {
                    int end = i;
                    if (end > start && all[end - 1] == '\r') {
                        end--;
                    }
                    if (end > start) {
                        String line = new String(all, start, end - start, UTF_8);
                        events.add(new ServerSentEvent(null, line));
                    }
                    start = i + 1;
                }
            }
            pending.reset();
            if (start < all.length) {
                pending.write(all, start, all.length - start);
            }
            return events;
        }

        @Override
        public List<ServerSentEvent> flush() {
            if (pending.size() == 0) {
                return List.of();
            }
            String line = pending.toString(UTF_8);
            pending.reset();
            return line.isEmpty() ? List.of() : List.of(new ServerSentEvent(null, line));
        }
    }
}
