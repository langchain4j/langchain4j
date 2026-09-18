///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:2.0.0.Beta3

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import jakarta.inject.Inject;

public class headers_mcp_server {

    @Inject
    private CurrentVertxRequest currentRequest;

    @Tool(description = "Returns the value of the given HTTP header")
    public String echoHeader(@ToolArg(description = "The name of the header to return") String headerName) {
        return currentRequest.getCurrent().request().getHeader(headerName);
    }

}
