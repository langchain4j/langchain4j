package dev.langchain4j.http.client.sse;

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

public class DefaultServerSentEventParser implements ServerSentEventParser {

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
        ServerSentEventParsingHandle parsingHandle = new DefaultServerSentEventParsingHandle(httpResponseBody);
        ServerSentEventContext context = new ServerSentEventContext(parsingHandle);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponseBody, UTF_8))) {

            String event = null;
            StringBuilder data = new StringBuilder();

            String line;
            while (!parsingHandle.isCancelled() && (line = reader.readLine()) != null) {
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

            if (!parsingHandle.isCancelled() && !data.isEmpty()) {
                ServerSentEvent sse = new ServerSentEvent(event, data.toString());
                ignoringExceptions(() -> listener.onEvent(sse, context));
            }
        } catch (IOException e) {
            ignoringExceptions(() -> listener.onError(e));
        }
    }

    @Override
    public Incremental incremental() {
        return new DefaultIncremental();
    }

    /**
     * Byte-driven SSE parser that mirrors the line-based logic of {@link #parse(InputStream, ServerSentEventListener)}.
     * Splits on {@code \n} byte boundaries (UTF-8 safe — {@code 0x0A} never appears as a continuation byte),
     * stripping an optional trailing {@code \r} per line.
     */
    private static final class DefaultIncremental implements Incremental {

        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private String currentEvent;
        private final StringBuilder currentData = new StringBuilder();

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
                    String line = new String(all, start, end - start, UTF_8);
                    processLine(line, events);
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
            List<ServerSentEvent> events = new ArrayList<>();
            if (pending.size() > 0) {
                processLine(pending.toString(UTF_8), events);
                pending.reset();
            }
            // Trailing event with no terminating blank line — match parse(InputStream)'s final emit.
            if (!currentData.isEmpty()) {
                events.add(new ServerSentEvent(currentEvent, currentData.toString()));
                currentEvent = null;
                currentData.setLength(0);
            }
            return events;
        }

        private void processLine(String line, List<ServerSentEvent> events) {
            if (line.isEmpty()) {
                if (!currentData.isEmpty()) {
                    events.add(new ServerSentEvent(currentEvent, currentData.toString()));
                    currentEvent = null;
                    currentData.setLength(0);
                }
                return;
            }
            if (line.startsWith("event:")) {
                currentEvent = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                String content = line.substring("data:".length());
                if (!currentData.isEmpty()) {
                    currentData.append("\n");
                }
                currentData.append(content.trim());
            }
            // Other SSE fields (id:, retry:, comment lines) intentionally ignored — same as parse().
        }
    }
}
