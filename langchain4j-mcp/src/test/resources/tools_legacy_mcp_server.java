///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.13.1
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:1.13.1

import java.util.List;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class tools_legacy_mcp_server {

    @Tool(description = "Echoes a string")
    String echoString(@ToolArg(description = "The string to be echoed") String input) {
        return input;
    }
}
