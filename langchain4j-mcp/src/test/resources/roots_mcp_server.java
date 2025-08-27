///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.25.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.4.0
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.4.0

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

// this server expects the client to provide a list of roots during initialization
// and provides a tool named 'assertRoots' that returns 'OK' if the
// roots were received correctly.
public class roots_mcp_server {

    private volatile List<Root> rootList;
    private CountDownLatch initializationLatch = new CountDownLatch(1);
    private CountDownLatch updateLatch = new CountDownLatch(1);

    @Notification(Type.INITIALIZED)
    void init(McpConnection connection, Roots roots) {
        if (!connection.initialRequest().supportsRoots()) {
            throw new RuntimeException("The client does not support roots.");
        }
        rootList = roots.listAndAwait();
        initializationLatch.countDown();
        Log.info("Roots list = " + rootList);
    }


    @Notification(Type.ROOTS_LIST_CHANGED)
    void change(McpConnection connection, Roots roots) {
        rootList = roots.listAndAwait();
        updateLatch.countDown();
    }

    @Tool
    String assertRoots() throws Exception {
        // Wait up to 20 seconds until the `rootList` variable has been set because that happens asynchronously
        // and the tool may be called before the MCP server receives the roots.
        initializationLatch.await(20, TimeUnit.SECONDS);
        return assertRoot("David's workspace", "file:///home/david/workspace");
    }

    @Tool
    String assertRootsAfterUpdate() throws Exception {
        updateLatch.await(20, TimeUnit.SECONDS);
        return assertRoot("Paul's workspace", "file:///home/paul/workspace");
    }

    private String assertRoot(String name, String uri) {
        if(rootList.isEmpty()) {
            throw new RuntimeException("The client didn't send any roots");
        }
        if(rootList.size() != 1) {
            throw new RuntimeException("The client sent more than one root: " + rootList);
        }
        Root root = rootList.get(0);
        if(!root.name().equals(name) || !root.uri().equals(uri)) {
            throw new RuntimeException("The client sent the wrong root: " + root);
        }
        return "OK";
    }

}
