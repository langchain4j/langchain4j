package dev.langchain4j.model.openai.realtime.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.openai.realtime.session.OpenAiRealtimeSession;
import dev.langchain4j.model.openai.realtime.tools.RealtimeToolLoop;
import dev.langchain4j.model.openai.realtime.tools.RealtimeToolRegistry;
import dev.langchain4j.model.openai.realtime.tools.SessionUpdateToolsRewriter;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One-to-one bridge between an inbound client and an outbound {@link OpenAiRealtimeSession}:
 * rewrites {@code session.update} tools, runs the server-side tool loop, and forwards events.
 */
public final class RealtimeGatewaySession implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenAiRealtimeSession outbound;
    private final SessionUpdateToolsRewriter rewriter;
    private final RealtimeToolLoop toolLoop;
    private final Consumer<String> inboundSink;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RealtimeGatewaySession(
            OpenAiRealtimeSession outbound,
            RealtimeToolRegistry registry,
            Executor toolExecutor,
            Consumer<String> inboundSink) {
        this.outbound = Objects.requireNonNull(outbound, "outbound");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(toolExecutor, "toolExecutor");
        this.inboundSink = Objects.requireNonNull(inboundSink, "inboundSink");
        this.rewriter = new SessionUpdateToolsRewriter(registry);
        this.toolLoop = new RealtimeToolLoop(registry, outbound::sendEvent, toolExecutor);
    }

    /**
     * Handle a text frame from the inbound client.
     */
    public void onClientMessage(String json) {
        Objects.requireNonNull(json, "json");
        if (closed.get()) {
            return;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                outbound.sendEvent(json);
                return;
            }
            String type = textOrEmpty(root, "type");
            if ("session.update".equals(type)) {
                handleSessionUpdate(json);
                return;
            }
            if (isClientFunctionCallOutput(root, type)) {
                inboundSink.accept(errorEvent("Clients must not send function_call_output; the gateway executes tools"));
                return;
            }
            outbound.sendEvent(json);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to handle client message", e);
        }
    }

    /**
     * Handle a text event from the outbound OpenAI Realtime session.
     * Invoked via the session listener (wire with {@code gatewayRef} in tests / server).
     */
    public void onOutboundEvent(String json) {
        Objects.requireNonNull(json, "json");
        if (closed.get()) {
            return;
        }
        toolLoop.onServerEvent(json);
        inboundSink.accept(json);
    }

    public void onClientClosed() {
        close();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        outbound.close();
    }

    private void handleSessionUpdate(String json) {
        try {
            String rewritten = rewriter.rewrite(json);
            outbound.sendEvent(rewritten);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "Invalid session.update" : e.getMessage();
            inboundSink.accept(errorEvent(message));
        }
    }

    private static boolean isClientFunctionCallOutput(JsonNode root, String type) {
        if ("function_call_output".equals(type)) {
            return true;
        }
        if ("conversation.item.create".equals(type)) {
            return "function_call_output".equals(root.path("item").path("type").asText());
        }
        return false;
    }

    private static String errorEvent(String message) {
        ObjectNode error = OBJECT_MAPPER.createObjectNode();
        error.put("message", message == null ? "" : message);
        ObjectNode event = OBJECT_MAPPER.createObjectNode();
        event.put("type", "error");
        event.set("error", error);
        try {
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"error\":{\"message\":\"error\"}}";
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText();
    }
}
