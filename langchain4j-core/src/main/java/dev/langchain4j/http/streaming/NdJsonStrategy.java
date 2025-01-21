package dev.langchain4j.http.streaming;

import dev.langchain4j.Experimental;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * TODO
 *
 * @see ServerSentEventStrategy
 */
@Experimental
public class NdJsonStrategy implements StreamingStrategy {
    // TODO name: NewLineStrategy? EventPerLineStrategy?

    // TODO review, refactor, test

    @Override
    public void process(InputStream inputStream, ServerSentEventListener listener) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onEvent(new ServerSentEvent(null, line));
            }
        } catch (IOException e) {
            listener.onError(e);
        }
    }
}
