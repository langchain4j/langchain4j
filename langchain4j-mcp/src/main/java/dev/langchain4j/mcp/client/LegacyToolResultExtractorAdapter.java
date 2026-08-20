package dev.langchain4j.mcp.client;

import dev.langchain4j.Internal;
import dev.langchain4j.mcp.client.transport.McpRawJson;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.List;
import java.util.Map;

/**
 * Presents a user-supplied {@link McpToolResultExtractor} as an {@link McpContentExtractor},
 * so that the client has a single extraction path. The content is rendered back into a Jackson
 * tree here, which is the cost of the deprecated interface and is confined to it.
 */
@Internal
@SuppressWarnings("removal")
class LegacyToolResultExtractorAdapter implements McpContentExtractor {

    private final McpToolResultExtractor delegate;

    LegacyToolResultExtractorAdapter(McpToolResultExtractor delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolExecutionResult extract(List<Map<String, Object>> content, boolean isError) {
        return delegate.extract(McpRawJson.parse(McpRawJson.serialize(content)), isError);
    }
}
