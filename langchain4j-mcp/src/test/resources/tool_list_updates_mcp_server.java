///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.20.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.1.0

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;

import io.quarkiverse.mcp.server.ToolManager;
import jakarta.inject.Inject;

// Server used for testing dynamic changes to the tool list
public class tool_list_updates_mcp_server {

    @Inject
    ToolManager toolManager;

    // we use this just as a way to tell the server that it should register a new tool
    @Tool
    public String registerNewTool() {
        toolManager.newTool("toLowerCase")
                .setDescription("Converts input string to lower case.")
                .addArgument("value", "Value to convert", true, String.class)
                .setHandler(
                        ta -> ToolResponse.success(ta.args().get("value").toString().toLowerCase()))
                .register();
        return "OK";
    }

}
