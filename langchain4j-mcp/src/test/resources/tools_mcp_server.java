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

public class tools_mcp_server {

    @Tool(description = "Echoes a string")
    public String echoString(@ToolArg(description = "The string to be echoed") String input) {
        return input;
    }

    @Tool(description = "Echoes an integer")
    public String echoInteger(@ToolArg(description = "The integer to be echoed") Integer input) {
        return String.valueOf(input);
    }

    @Tool(description = "Echoes a boolean")
    public String echoBoolean(@ToolArg(description = "The boolean to be echoed") Boolean input) {
        return Boolean.valueOf(input).toString();
    }

    @Tool(description = "Takes 10 seconds to complete")
    public String longOperation() throws Exception {
        TimeUnit.SECONDS.sleep(10);
        return "ok";
    }

    @Tool(description = "Throws a business error")
    public String error() throws Exception {
        throw new RuntimeException("business error");
    }
    
    @Tool(description = "Returns a response as an error")
    public ToolResponse errorResponse() throws Exception {
        List<TextContent> lst = new ArrayList<>();
        lst.add(new TextContent("This is an actual error"));
        return new ToolResponse(true, lst);
    }

}
