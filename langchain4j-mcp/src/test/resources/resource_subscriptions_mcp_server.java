///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3

import java.util.concurrent.atomic.AtomicReference;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;

import jakarta.inject.Inject;

// Server used for testing resource change subscriptions
public class resource_subscriptions_mcp_server {

    @Inject
    ResourceManager resourceManager;

    private final AtomicReference<String> statusValue = new AtomicReference<>("initial");
    private final AtomicReference<String> counterValue = new AtomicReference<>("zero");

    @Resource(uri = "file:///status", description = "A status resource", mimeType = "text/plain")
    TextResourceContents status() {
        return TextResourceContents.create("file:///status", statusValue.get());
    }

    @Resource(uri = "file:///counter", description = "A counter resource", mimeType = "text/plain")
    TextResourceContents counter() {
        return TextResourceContents.create("file:///counter", counterValue.get());
    }

    @Tool(description = "Updates the status resource")
    public String updateStatus(String newValue) {
        statusValue.set(newValue);
        resourceManager.getResource("file:///status").sendUpdateAndForget();
        return "OK";
    }

    @Tool(description = "Updates the counter resource")
    public String updateCounter(String newValue) {
        counterValue.set(newValue);
        resourceManager.getResource("file:///counter").sendUpdateAndForget();
        return "OK";
    }

}
