package dev.langchain4j.model.openai.realtime.session;

import java.util.Map;

/**
 * Pluggable outbound WebSocket transport for OpenAI Realtime.
 */
public interface RealtimeTransport extends AutoCloseable {

    void connect(String url, Map<String, String> headers, RealtimeTransportListener listener);

    void sendText(String message);

    @Override
    void close();
}
