///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.24.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.3.1
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.3.1
//DEPS org.awaitility:awaitility:4.3.0

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkus.logging.Log;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Root;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import org.awaitility.Awaitility;

// this server expects the client to provide a list of roots during initialization
// and provides a tool named 'assertRoots' that returns 'OK' if the
// roots were received correctly.
public class roots_mcp_server {

    private volatile List<Root> rootList;

    @Notification(Type.INITIALIZED)
    void init(McpConnection connection, Roots roots) {
        if (!connection.initialRequest().supportsRoots()) {
            throw new RuntimeException("The client does not support roots.");
        }
        rootList = roots.listAndAwait();
        Log.info("Roots list = " + rootList);
    }

    @Tool
    String assertRoots() {
        // Wait up to 20 seconds until the `rootList` variable has been set because that happens asynchronously
        // and the tool may be called before the MCP server receives the roots.
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> rootList != null);
        if(rootList.isEmpty()) {
            throw new RuntimeException("The client didn't send any roots");
        }
        if(rootList.size() != 1) {
            throw new RuntimeException("The client sent more than one root: " + rootList);
        }
        Root root = rootList.get(0);
        if(!root.name().equals("David's workspace") || !root.uri().equals("file:///home/david/workspace")) {
            throw new RuntimeException("The client sent the wrong root: " + root);
        }
        return "OK";
    }

}
