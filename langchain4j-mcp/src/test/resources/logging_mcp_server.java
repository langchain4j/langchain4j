///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.25.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.5.3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.5.3
//Q:CONFIG quarkus.mcp.server.client-logging.default-level=DEBUG

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class logging_mcp_server {

    @Tool(description = "Log an INFO-level message")
    public String info(McpLog log) {
        // put an additional 'data:' into the message just to test that it doesn't break the SSE event parsing logic
        log.info("HELLO. data: 1234");
        return "ok";
    }

    @Tool(description = "Log a DEBUG-level message")
    public String debug(McpLog log) {
        log.debug("HELLO DEBUG");
        return "ok";
    }

}
