package dev.langchain4j.http.client;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * TODO
 */
@Experimental
public class DefaultServerSentEventParser implements ServerSentEventParser {

    // TODO review, refactor, test

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponseBody))) {

            String event = null;
            StringBuilder data = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        listener.onEvent(new ServerSentEvent(event, data.toString()));
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
                    data.append(content);
                }
            }

            if (!data.isEmpty()) {
                listener.onEvent(new ServerSentEvent(event, data.toString()));
            }
        } catch (IOException e) {
            listener.onError(e);
        }
    }
}
