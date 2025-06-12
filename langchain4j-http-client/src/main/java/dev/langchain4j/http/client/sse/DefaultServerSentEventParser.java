package dev.langchain4j.http.client.sse;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DefaultServerSentEventParser implements ServerSentEventParser {

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponseBody))) {

            String event = null;
            StringBuilder data = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        ServerSentEvent sse = new ServerSentEvent(event, data.toString());
                        ignoringExceptions(() -> listener.onEvent(sse));
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
                ignoringExceptions(() -> listener.onEvent(sse));
            }
        } catch (IOException e) {
            ignoringExceptions(() -> listener.onError(e));
        }
    }
}
