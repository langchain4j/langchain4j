///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.11.0

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;

import jakarta.inject.Inject;

// Server used for testing resource list change notifications
public class resource_list_changes_mcp_server {

    @Inject
    ResourceManager resourceManager;

    @Resource(uri = "file:///static", description = "A static resource", mimeType = "text/plain")
    TextResourceContents staticResource() {
        return TextResourceContents.create("file:///static", "static-value");
    }

    // Tool to register a new dynamic resource (triggers list_changed notification)
    @Tool(description = "Registers a new dynamic resource")
    public String registerNewResource() {
        resourceManager.newResource("dynamic")
                .setUri("file:///dynamic")
                .setDescription("A dynamically added resource")
                .setMimeType("text/plain")
                .setHandler(
                        args -> new ResourceResponse(
                                TextResourceContents.create(
                                        args.requestUri().value(), "dynamic-value")))
                .register();
        return "OK";
    }

}
