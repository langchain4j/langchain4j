///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.17.5}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.0.0.Alpha6
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.0.0.Alpha6
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
        log.info("HELLO");
        return "ok";
    }

    @Tool(description = "Log a DEBUG-level message")
    public String debug(McpLog log) {
        log.debug("HELLO DEBUG");
        return "ok";
    }

}
