///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:2.0.0.Beta3

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
