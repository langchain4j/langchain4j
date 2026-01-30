///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.27.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.8.1

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.inject.Inject;

public class listener_mcp_server {


    @Tool
    public String nothing() {
        return "OK";
    }

    @Tool
    public ToolResponse withApplicationLevelError() {
        return ToolResponse.error("Application-level error");
    }

    @Tool
    public ToolResponse withProtocolError() {
        throw new RuntimeException("Protocol error");
    }

    @Tool
    public String longOperation() throws Exception {
        TimeUnit.SECONDS.sleep(5);
        return "FINISHED";
    }

    @Resource(uri = "file:///test-resource", description = "Test resource for listener", mimeType = "text/plain")
    public TextResourceContents testResource() {
        return TextResourceContents.create("file:///test-resource", "Test resource content");
    }

    @Resource(uri = "file:///test-resource-failing", mimeType = "text/plain")
    TextResourceContents testResourceFailing() {
        throw new RuntimeException("Can't read this resource!");
    }

    @Prompt
    public PromptMessage testPrompt() {
        return PromptMessage.withUserRole(new TextContent("Test prompt message"));
    }

    @Prompt
    public PromptMessage testPromptFailing() {
        throw new RuntimeException("Can't read this prompt!");
    }

}
