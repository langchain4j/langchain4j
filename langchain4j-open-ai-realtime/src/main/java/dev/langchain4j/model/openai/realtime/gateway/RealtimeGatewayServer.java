package dev.langchain4j.model.openai.realtime.gateway;

import dev.langchain4j.model.openai.realtime.session.OpenAiRealtimeSession;
import dev.langchain4j.model.openai.realtime.session.OpenAiRealtimeSessionListener;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inbound WebSocket server: one client connection maps to one outbound Realtime session.
 */
public final class RealtimeGatewayServer {

    private static final Logger log = LoggerFactory.getLogger(RealtimeGatewayServer.class);

    private final RealtimeGatewayConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile ServerImpl server;

    public RealtimeGatewayServer(RealtimeGatewayConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("gateway server already started");
        }
        CountDownLatch ready = new CountDownLatch(1);
        ServerImpl impl = new ServerImpl(new InetSocketAddress(config.host(), config.port()), config, ready);
        this.server = impl;
        impl.setReuseAddr(true);
        impl.start();
        try {
            if (!ready.await(10, TimeUnit.SECONDS)) {
                stopQuietly();
                throw new IllegalStateException("gateway server failed to start within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopQuietly();
            throw new IllegalStateException("interrupted while starting gateway server", e);
        }
        log.info("Realtime gateway listening on {}", wsUri());
    }

    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        stopQuietly();
    }

    public URI wsUri() {
        ServerImpl impl = server;
        if (impl == null || !started.get()) {
            throw new IllegalStateException("gateway server is not started");
        }
        return URI.create("ws://" + config.host() + ":" + impl.getPort() + "/");
    }

    public int port() {
        ServerImpl impl = server;
        if (impl == null || !started.get()) {
            throw new IllegalStateException("gateway server is not started");
        }
        return impl.getPort();
    }

    private void stopQuietly() {
        ServerImpl impl = server;
        server = null;
        if (impl != null) {
            try {
                impl.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("Error stopping gateway server", e);
            }
        }
    }

    private static final class ServerImpl extends WebSocketServer {

        private final RealtimeGatewayConfig config;
        private final CountDownLatch ready;

        ServerImpl(InetSocketAddress address, RealtimeGatewayConfig config, CountDownLatch ready) {
            super(address);
            this.config = config;
            this.ready = ready;
        }

        @Override
        public void onStart() {
            ready.countDown();
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String apiKey = extractBearerToken(handshake.getFieldValue("Authorization"));
            if (apiKey == null) {
                log.warn("Rejecting inbound connection without Authorization Bearer");
                conn.close(1008, "Missing Authorization Bearer");
                return;
            }

            AtomicReference<RealtimeGatewaySession> gatewayRef = new AtomicReference<>();
            OpenAiRealtimeSession outbound = OpenAiRealtimeSession.builder()
                    .transport(config.outboundTransportFactory().apply(apiKey))
                    .apiKey(apiKey)
                    .listener(new OpenAiRealtimeSessionListener() {
                        @Override
                        public void onEvent(String json) {
                            RealtimeGatewaySession session = gatewayRef.get();
                            if (session != null) {
                                session.onOutboundEvent(json);
                            }
                        }

                        @Override
                        public void onClosed(int code, String reason) {
                            RealtimeGatewaySession session = gatewayRef.get();
                            if (session != null) {
                                session.onOutboundClosed(code, reason);
                            }
                            closeInboundQuietly(conn, 1011, "Outbound closed");
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            RealtimeGatewaySession session = gatewayRef.get();
                            if (session != null) {
                                session.onOutboundFailure(t);
                            }
                            closeInboundQuietly(conn, 1011, "Outbound failed");
                        }
                    })
                    .build();

            RealtimeGatewaySession gatewaySession = new RealtimeGatewaySession(
                    outbound,
                    config.toolRegistry(),
                    config.toolExecutor(),
                    message -> sendToClient(conn, message));
            gatewayRef.set(gatewaySession);
            conn.setAttachment(gatewaySession);

            try {
                outbound.connect();
            } catch (RuntimeException e) {
                log.warn("Failed to open outbound Realtime session", e);
                gatewaySession.close();
                conn.close(1011, "Outbound connect failed");
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            RealtimeGatewaySession session = conn.getAttachment();
            if (session != null) {
                session.onClientClosed();
            }
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            RealtimeGatewaySession session = conn.getAttachment();
            if (session != null) {
                session.onClientMessage(message);
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            log.warn("Inbound WebSocket error", ex);
            if (conn != null) {
                RealtimeGatewaySession session = conn.getAttachment();
                if (session != null) {
                    session.close();
                }
            }
        }

        private static void sendToClient(WebSocket conn, String message) {
            if (conn == null || !conn.isOpen()) {
                return;
            }
            try {
                conn.send(message);
            } catch (Exception e) {
                log.debug("Failed to send inbound message", e);
            }
        }

        private static void closeInboundQuietly(WebSocket conn, int code, String reason) {
            if (conn == null || !conn.isOpen()) {
                return;
            }
            try {
                conn.close(code, reason);
            } catch (Exception e) {
                log.debug("Failed to close inbound WebSocket", e);
            }
        }

        private static String extractBearerToken(String authorization) {
            if (authorization == null || authorization.isBlank()) {
                return null;
            }
            String value = authorization.trim();
            if (value.length() > 7 && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                String token = value.substring(7).trim();
                return token.isEmpty() ? null : token;
            }
            return null;
        }
    }
}
