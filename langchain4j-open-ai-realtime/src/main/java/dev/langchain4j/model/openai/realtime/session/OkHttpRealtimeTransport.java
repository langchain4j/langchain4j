package dev.langchain4j.model.openai.realtime.session;

import java.util.Map;
import java.util.Objects;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * OkHttp-based {@link RealtimeTransport} for OpenAI Realtime WebSocket.
 */
public final class OkHttpRealtimeTransport implements RealtimeTransport {

    private final OkHttpClient client;
    private volatile WebSocket webSocket;

    public OkHttpRealtimeTransport() {
        this(new OkHttpClient());
    }

    OkHttpRealtimeTransport(OkHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void connect(String url, Map<String, String> headers, RealtimeTransportListener listener) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(listener, "listener");

        Request.Builder requestBuilder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }

        webSocket = client.newWebSocket(requestBuilder.build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                listener.onOpen();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                listener.onTextMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                listener.onClosed(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                listener.onFailure(t);
            }
        });
    }

    @Override
    public void sendText(String message) {
        WebSocket ws = webSocket;
        if (ws == null) {
            throw new IllegalStateException("not connected");
        }
        ws.send(message);
    }

    @Override
    public void close() {
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.close(1000, "closed");
            webSocket = null;
        }
    }
}
