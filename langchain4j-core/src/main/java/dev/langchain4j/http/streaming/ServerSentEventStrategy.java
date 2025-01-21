package dev.langchain4j.http.streaming;

import dev.langchain4j.Experimental;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * TODO
 *
 * @see NdJsonStrategy
 */
@Experimental
public class ServerSentEventStrategy implements StreamingStrategy {

    // TODO do not release yet
    // TODO review, refactor, test

    @Override
    public void process(InputStream inputStream, ServerSentEventListener listener) {

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String event = null;
            StringBuilder data = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
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
