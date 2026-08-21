///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:2.0.0.Beta3

import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Tool;

public class progress_mcp_server {

    @Tool(description = "A tool that reports progress notifications")
    public String progressOperation(Progress progress) {
        if (progress.token().isEmpty()) {
            return "no-progress-token";
        }
        for (int i = 1; i <= 3; i++) {
            progress.notificationBuilder()
                    .setProgress(i)
                    .setTotal(3)
                    .setMessage("Step " + i + " of 3")
                    .build()
                    .sendAndForget();
        }
        return "done";
    }
}
