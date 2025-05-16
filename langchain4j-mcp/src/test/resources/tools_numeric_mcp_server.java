///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.20.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.1.0

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;

public class tools_numeric_mcp_server {

    @Tool(description = "Echoes a long")
    public String echoLong(@ToolArg(description = "The long to be echoed") Long input) {
        return String.valueOf(input);
    }

    @Tool(description = "Echoes an integer plus one")
    public String echoInteger(@ToolArg(description = "The integer to be echoed") Integer input) {
        return String.valueOf(input+1);
    }

    @Tool(description = "Echoes a double")
    public String echoDouble(@ToolArg(description = "The double to be echoed") Double input) {
        return String.valueOf(input);
    }

    @Tool(description = "Echoes a float")
    public String echoFloat(@ToolArg(description = "The float to be echoed") Float input) {
        return String.valueOf(input);
    }
}
