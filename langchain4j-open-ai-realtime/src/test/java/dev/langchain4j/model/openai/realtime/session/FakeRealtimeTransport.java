package dev.langchain4j.model.openai.realtime.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link RealtimeTransport} for unit tests.
 */
public final class FakeRealtimeTransport implements RealtimeTransport {

    public final List<String> sent = Collections.synchronizedList(new ArrayList<>());

    public volatile String connectedUrl;
    public volatile Map<String, String> connectedHeaders;
    public volatile RealtimeTransportListener listener;
    public volatile boolean closed;

    @Override
    public void connect(String url, Map<String, String> headers, RealtimeTransportListener listener) {
        this.connectedUrl = url;
        this.connectedHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.listener = listener;
        listener.onOpen();
    }

    @Override
    public void sendText(String message) {
        sent.add(message);
    }

    @Override
    public void close() {
        closed = true;
    }

    public void simulateText(String text) {
        if (listener == null) {
            throw new IllegalStateException("not connected");
        }
        listener.onTextMessage(text);
    }
}
