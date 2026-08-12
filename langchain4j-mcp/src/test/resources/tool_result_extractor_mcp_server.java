///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.27.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.7.2

import java.util.List;

import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;

public class tool_result_extractor_mcp_server {

    public record Payload(Integer value, String status) {}

    @Tool(description = "Returns JSON as plain text content")
    public String jsonContent() {
        return "{\"value\":42,\"status\":\"ok\"}";
    }

    @Tool(description = "Returns image content")
    public ToolResponse imageContent() {
        return new ToolResponse(false, List.of(new ImageContent("iVBORw0KGgo=", "image/png")));
    }

    @Tool(description = "Returns structured content", structuredContent = true)
    public Payload structuredContent() {
        return new Payload(7, "structured");
    }

    @Tool(description = "Returns mixed text and image content")
    public ToolResponse mixedContent() {
        return new ToolResponse(false, List.of(
                new TextContent("preview"),
                new ImageContent("iVBORw0KGgo=", "image/png")));
    }
}
