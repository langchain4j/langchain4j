package dev.langchain4j.model.openai.realtime.tools;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps {@link ToolSpecification} to OpenAI Realtime function-tool JSON
 * (flat {@code type/name/description/parameters}, not Chat Completions nested shape).
 */
public final class OpenAiRealtimeToolJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OpenAiRealtimeToolJson() {}

    public static ObjectNode toFunctionTool(ToolSpecification toolSpecification) {
        Objects.requireNonNull(toolSpecification, "toolSpecification");

        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "function");
        node.put("name", toolSpecification.name());
        if (toolSpecification.description() != null) {
            node.put("description", toolSpecification.description());
        }
        node.set("parameters", OBJECT_MAPPER.valueToTree(toOpenAiParameters(toolSpecification)));
        return node;
    }

    private static Map<String, Object> toOpenAiParameters(ToolSpecification toolSpecification) {
        if (toolSpecification.parameters() != null) {
            return toMap(toolSpecification.parameters());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");
        map.put("properties", new HashMap<>());
        map.put("required", new ArrayList<>());
        return map;
    }
}
