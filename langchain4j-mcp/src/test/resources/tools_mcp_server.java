///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.25.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.4.0
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.4.0

import java.util.ArrayList;
import java.util.Arrays;
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

    @Tool(description = "Takes an untyped array")
    public String untypedArray(Object[] arr) throws Exception {
        // note: I would return something like 'wrong' and 'correct' here but that 'wrong' seems to cause the model to keep retrying the call.
        // so, 6789 is considered to be the expected output
        if(Arrays.equals(arr, new Object[] {0, "abs", null})) {
            return "6789";
        } else {
            return "1234";
        }
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

    @Tool
    public ToolResponse getWeatherThrowingException(String arg0) {
        return new ToolResponse(true, List.of(new TextContent("Weather service is unavailable")));
    }

    @Tool
    public String getWeather(String arg0) {
        return "Sunny";
    }
}
