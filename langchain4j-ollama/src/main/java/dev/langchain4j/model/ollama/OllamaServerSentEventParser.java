package dev.langchain4j.model.ollama;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponseBody))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ServerSentEvent sse = new ServerSentEvent(null, line);
                ignoringExceptions(() -> listener.onEvent(sse));
            }
        } catch (IOException e) {
            ignoringExceptions(() -> listener.onError(e));
        }
    }
}
