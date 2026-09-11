///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.http.McpParamHeader;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.inject.Inject;

public class param_header_mcp_server {

    @Inject
    private CurrentVertxRequest currentRequest;

    @Tool(description = "Echoes the Mcp-Param-region HTTP header value")
    public String regionEcho(
            @McpParamHeader @ToolArg(description = "The region") String region,
            @ToolArg(description = "The value") String value) {
        String headerValue = currentRequest.getCurrent().request().getHeader("Mcp-Param-region");
        return "header=" + headerValue + ",body=" + region;
    }

    @Tool(description = "Echoes the Mcp-Param-MyRegion HTTP header value")
    public String customHeaderName(
            @McpParamHeader("MyRegion") @ToolArg(description = "The region") String region,
            @ToolArg(description = "The value") String value) {
        String headerValue = currentRequest.getCurrent().request().getHeader("Mcp-Param-MyRegion");
        return "header=" + headerValue + ",body=" + region;
    }

    @Tool(description = "Echoes the Mcp-Param-count and Mcp-Param-verbose HTTP header values")
    public String typedHeaders(
            @McpParamHeader @ToolArg(description = "A count") int count,
            @McpParamHeader @ToolArg(description = "Is verbose") boolean verbose,
            @ToolArg(description = "The value") String value) {
        String countHeader = currentRequest.getCurrent().request().getHeader("Mcp-Param-count");
        String verboseHeader = currentRequest.getCurrent().request().getHeader("Mcp-Param-verbose");
        return "countHeader=" + countHeader + ",verboseHeader=" + verboseHeader;
    }
}
