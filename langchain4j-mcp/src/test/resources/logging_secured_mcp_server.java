///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.20.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.1.0
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.1.0
//Q:CONFIG quarkus.mcp.server.client-logging.default-level=DEBUG

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class logging_mcp_server {

    @AuthenticationHandler(BearerTokenAuthenticationHandler.class)
    public static class BearerTokenAuthenticationHandler implements AuthenticationHandler {

        private static final String BEARER_TOKEN = "your_secret_bearer_token";

        @Override
        public boolean authenticate(McpRequest request, McpResponse response) {
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader!= null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);
                if (token.equals(BEARER_TOKEN)) {
                    return true;
                }
            }
            response.setStatus(401);
            response.setContentType("text/plain");
            response.setBody("Unauthorized");
            return false;
        }
    }

    @Tool(description = "Log an INFO-level message")
    public String info(McpLog log) {
        log.info("HELLO");
        return "ok";
    }

    @Tool(description = "Log a DEBUG-level message")
    public String debug(McpLog log) {
        log.debug("HELLO DEBUG");
        return "ok";
    }

}
