///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.27.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.7.2
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.7.2
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:1.7.2

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;

public class tools_mcp_server {

    @Tool(name = "writer", description = "Write a story about a given topic")
    String writer(@ToolArg(description = "The topic for a new story") String topic) {
        return "This is a funny story about " + topic;
    }

    @Tool(name = "styleScorer", description = "Score the style of a story")
    String styleScorer(@ToolArg(description = "The story to be evaluated") String story,
                       @ToolArg(description = "The style of the story") String style) {
        return "0.9";
    }
}
