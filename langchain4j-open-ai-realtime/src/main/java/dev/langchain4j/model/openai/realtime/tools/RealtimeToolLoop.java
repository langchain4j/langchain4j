package dev.langchain4j.model.openai.realtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Server-side function-call loop: on {@code response.done} with function calls,
 * execute tools in parallel, emit {@code function_call_output} items, then one {@code response.create}.
 */
public final class RealtimeToolLoop {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RealtimeToolRegistry registry;
    private final RealtimeOutboundWriter writer;
    private final Executor toolExecutor;

    public RealtimeToolLoop(
            RealtimeToolRegistry registry, RealtimeOutboundWriter writer, Executor toolExecutor) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor");
    }

    /**
     * @return true if handled (had function_call(s) and ran loop)
     */
    public boolean onServerEvent(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return false;
            }
            JsonNode typeNode = root.get("type");
            if (typeNode == null || !"response.done".equals(typeNode.asText())) {
                return false;
            }
            JsonNode output = root.path("response").path("output");
            if (!output.isArray()) {
                return false;
            }

            List<JsonNode> functionCalls = new ArrayList<>();
            for (JsonNode item : output) {
                if (item != null && "function_call".equals(item.path("type").asText())) {
                    functionCalls.add(item);
                }
            }
            if (functionCalls.isEmpty()) {
                return false;
            }

            List<CompletableFuture<FunctionCallOutcome>> futures = new ArrayList<>(functionCalls.size());
            for (JsonNode call : functionCalls) {
                futures.add(CompletableFuture.supplyAsync(() -> executeCall(call), toolExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<FunctionCallOutcome> future : futures) {
                FunctionCallOutcome outcome = future.join();
                writer.send(toFunctionCallOutputEvent(outcome.callId, outcome.output));
            }
            writer.send("{\"type\":\"response.create\"}");
            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to handle realtime server event", e);
        }
    }

    private FunctionCallOutcome executeCall(JsonNode call) {
        String callId = textOrEmpty(call, "call_id");
        String name = textOrEmpty(call, "name");
        String arguments = textOrNull(call, "arguments");
        if (arguments == null) {
            arguments = "{}";
        }

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(callId)
                .name(name)
                .arguments(arguments)
                .build();

        ToolExecutor executor = registry.findExecutor(name);
        if (executor == null) {
            return new FunctionCallOutcome(callId, "{\"error\":\"tool not found: " + name + "\"}");
        }
        try {
            String result = executor.execute(request, null);
            return new FunctionCallOutcome(callId, result == null ? "" : result);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new FunctionCallOutcome(callId, "{\"error\":\"" + escapeJson(message) + "\"}");
        }
    }

    private static String toFunctionCallOutputEvent(String callId, String output) {
        try {
            ObjectNode item = OBJECT_MAPPER.createObjectNode();
            item.put("type", "function_call_output");
            item.put("call_id", callId);
            item.put("output", output == null ? "" : output);

            ObjectNode event = OBJECT_MAPPER.createObjectNode();
            event.put("type", "conversation.item.create");
            event.set("item", item);
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize function_call_output", e);
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class FunctionCallOutcome {
        final String callId;
        final String output;

        FunctionCallOutcome(String callId, String output) {
            this.callId = callId;
            this.output = output;
        }
    }
}
