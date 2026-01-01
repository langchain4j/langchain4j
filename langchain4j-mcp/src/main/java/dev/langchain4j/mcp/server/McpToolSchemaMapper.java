package dev.langchain4j.mcp.server;

import static dev.langchain4j.mcp.client.McpToolMetadataKeys.DESTRUCTIVE_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.IDEMPOTENT_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.OPEN_WORLD_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.READ_ONLY_HINT;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.TITLE;
import static dev.langchain4j.mcp.client.McpToolMetadataKeys.TITLE_ANNOTATION;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpToolSchemaMapper {

    public List<Map<String, Object>> toMcpTools(List<ToolSpecification> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolSpecification tool : tools) {
            result.add(toMcpTool(tool));
        }
        return result;
    }

    public Map<String, Object> toMcpTool(ToolSpecification tool) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tool.name());
        if (tool.description() != null) {
            map.put("description", tool.description());
        }

        JsonObjectSchema parameters = tool.parameters();
        JsonObjectSchema schema = parameters != null
                ? parameters
                : JsonObjectSchema.builder().build();
        map.put("inputSchema", JsonSchemaElementUtils.toMap(schema));

        addMetadata(tool.metadata(), map);

        return map;
    }

    private void addMetadata(Map<String, Object> metadata, Map<String, Object> target) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        Map<String, Object> annotations = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (TITLE.equals(key)) {
                target.put("title", value);
            } else if (TITLE_ANNOTATION.equals(key)) {
                annotations.put("title", value);
            } else if (READ_ONLY_HINT.equals(key)
                    || DESTRUCTIVE_HINT.equals(key)
                    || IDEMPOTENT_HINT.equals(key)
                    || OPEN_WORLD_HINT.equals(key)) {
                annotations.put(key, value);
            } else {
                meta.put(key, value);
            }
        }

        if (!annotations.isEmpty()) {
            target.put("annotations", annotations);
        }
        if (!meta.isEmpty()) {
            target.put("_meta", meta);
        }
    }
}
