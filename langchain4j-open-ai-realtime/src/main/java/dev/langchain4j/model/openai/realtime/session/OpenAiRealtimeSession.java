package dev.langchain4j.model.openai.realtime.session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbound OpenAI Realtime WebSocket session: connect, serialized send, and inbound JSON events.
 */
public final class OpenAiRealtimeSession implements AutoCloseable {

    private static final String DEFAULT_MODEL = "gpt-realtime-2.1";
    private static final String DEFAULT_BASE_URL = "wss://api.openai.com/v1/realtime";

    private final RealtimeTransport transport;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final OpenAiRealtimeSessionListener listener;
    private final String safetyIdentifier;

    private final BlockingQueue<String> outboundQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean writerRunning = new AtomicBoolean(false);
    private volatile Thread writerThread;

    private OpenAiRealtimeSession(Builder builder) {
        this.transport = Objects.requireNonNull(builder.transport, "transport");
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey");
        this.model = builder.model == null || builder.model.isBlank() ? DEFAULT_MODEL : builder.model;
        this.baseUrl = builder.baseUrl;
        this.listener = Objects.requireNonNull(builder.listener, "listener");
        this.safetyIdentifier = builder.safetyIdentifier;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void connect() {
        startWriter();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        if (safetyIdentifier != null && !safetyIdentifier.isBlank()) {
            headers.put("OpenAI-Safety-Identifier", safetyIdentifier);
        }
        transport.connect(buildUrl(), headers, new RealtimeTransportListener() {
            @Override
            public void onOpen() {
                // no-op: session readiness is connection completion
            }

            @Override
            public void onTextMessage(String text) {
                listener.onEvent(text);
            }

            @Override
            public void onClosed(int code, String reason) {
                listener.onClosed(code, reason);
            }

            @Override
            public void onFailure(Throwable t) {
                listener.onFailure(t);
            }
        });
    }

    /**
     * Enqueues a client event JSON string; writes are serialized on a single writer thread.
     */
    public void sendEvent(String json) {
        Objects.requireNonNull(json, "json");
        if (!writerRunning.get()) {
            throw new IllegalStateException("session is not connected");
        }
        outboundQueue.offer(json);
    }

    @Override
    public void close() {
        stopWriter();
        transport.close();
    }

    private String buildUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            if (baseUrl.contains("model=")) {
                return baseUrl;
            }
            String sep = baseUrl.contains("?") ? "&" : "?";
            return baseUrl + sep + "model=" + model;
        }
        return DEFAULT_BASE_URL + "?model=" + model;
    }

    private void startWriter() {
        if (!writerRunning.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::writeLoop, "openai-realtime-outbound-writer");
        thread.setDaemon(true);
        writerThread = thread;
        thread.start();
    }

    private void writeLoop() {
        try {
            while (writerRunning.get() || !outboundQueue.isEmpty()) {
                String message = outboundQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message != null) {
                    transport.sendText(message);
                } else if (!writerRunning.get()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void stopWriter() {
        writerRunning.set(false);
        Thread thread = writerThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writerThread = null;
        }
    }

    public static final class Builder {

        private RealtimeTransport transport;
        private String apiKey;
        private String model = DEFAULT_MODEL;
        private String baseUrl;
        private OpenAiRealtimeSessionListener listener;
        private String safetyIdentifier;

        public Builder transport(RealtimeTransport transport) {
            this.transport = transport;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder listener(OpenAiRealtimeSessionListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder safetyIdentifier(String safetyIdentifier) {
            this.safetyIdentifier = safetyIdentifier;
            return this;
        }

        public OpenAiRealtimeSession build() {
            return new OpenAiRealtimeSession(this);
        }
    }
}
