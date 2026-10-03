package dev.langchain4j.model.openai.realtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rewrites {@code session.update} tools from client name whitelist to registry schemas.
 */
public final class SessionUpdateToolsRewriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RealtimeToolRegistry registry;

    public SessionUpdateToolsRewriter(RealtimeToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public String rewrite(String inboundJson) {
        Objects.requireNonNull(inboundJson, "inboundJson");
        try {
            JsonNode root = OBJECT_MAPPER.readTree(inboundJson);
            if (!root.isObject()) {
                return inboundJson;
            }
            JsonNode typeNode = root.get("type");
            if (typeNode == null || !"session.update".equals(typeNode.asText())) {
                return inboundJson;
            }

            ObjectNode rootObject = (ObjectNode) root;
            JsonNode sessionNode = rootObject.get("session");
            ObjectNode session;
            if (sessionNode != null && sessionNode.isObject()) {
                session = (ObjectNode) sessionNode;
            } else {
                session = rootObject.putObject("session");
            }
            JsonNode toolsNode = session.get("tools");

            List<ToolSpecification> specs;
            if (toolsNode == null || toolsNode.isNull() || (toolsNode.isArray() && toolsNode.isEmpty())) {
                specs = registry.allSpecifications();
            } else if (!toolsNode.isArray()) {
                throw new IllegalArgumentException("session.tools must be an array");
            } else {
                specs = resolveWhitelist((ArrayNode) toolsNode);
            }

            ArrayNode rewritten = OBJECT_MAPPER.createArrayNode();
            for (ToolSpecification spec : specs) {
                rewritten.add(OpenAiRealtimeToolJson.toFunctionTool(spec));
            }
            session.set("tools", rewritten);
            return OBJECT_MAPPER.writeValueAsString(rootObject);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to rewrite session.update tools", e);
        }
    }

    private List<ToolSpecification> resolveWhitelist(ArrayNode toolsNode) {
        List<ToolSpecification> specs = new ArrayList<>();
        for (JsonNode toolNode : toolsNode) {
            String name = extractToolName(toolNode);
            ToolSpecification spec = registry.findSpecification(name);
            if (spec == null) {
                throw new IllegalArgumentException("Unknown tool name: " + name);
            }
            specs.add(spec);
        }
        return specs;
    }

    private static String extractToolName(JsonNode toolNode) {
        if (toolNode == null || !toolNode.isObject()) {
            throw new IllegalArgumentException("Each session.tools entry must be an object");
        }
        JsonNode nameNode = toolNode.get("name");
        if (nameNode != null && !nameNode.isNull() && nameNode.isTextual()) {
            return nameNode.asText();
        }
        JsonNode functionNode = toolNode.get("function");
        if (functionNode != null && functionNode.isObject()) {
            JsonNode nestedName = functionNode.get("name");
            if (nestedName != null && !nestedName.isNull() && nestedName.isTextual()) {
                return nestedName.asText();
            }
        }
        throw new IllegalArgumentException("Tool entry is missing name");
    }
}
