package dev.langchain4j.http.client.sse;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
                    event = stripSingleLeadingSpace(line.substring("event:".length()));
                } else if (line.startsWith("data:")) {
                    String content = stripSingleLeadingSpace(line.substring("data:".length()));
                    if (!data.isEmpty()) {
                        data.append("\n");
                    }
                    data.append(content);
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

    /**
     * Strips at most a single leading U+0020 SPACE from the given value, as required by the
     * <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#parsing-an-event-stream">
     * WHATWG HTML Living Standard</a> for server-sent events. All other whitespace (additional
     * leading spaces, trailing spaces, tabs, etc.) is preserved.
     *
     * @param value the raw field value (text after the field name and colon)
     * @return the value with at most one leading space removed
     */
    private static String stripSingleLeadingSpace(String value) {
        return value.startsWith(" ") ? value.substring(1) : value;
    }
}
