///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.11.0

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

    @Resource(uri = "file:///status", description = "A status resource", mimeType = "text/plain")
    TextResourceContents status() {
        return TextResourceContents.create("file:///status", statusValue.get());
    }

    // Tool to update the status resource and notify subscribed clients
    @Tool(description = "Updates the status resource")
    public String updateStatus(String newValue) {
        statusValue.set(newValue);
        resourceManager.getResource("file:///status").sendUpdateAndForget();
        return "OK";
    }

}
