///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:2.0.0.Beta3

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;

public class prompts_mcp_server {

    @Prompt(description = "Basic simple prompt")
    PromptMessage basic() {
        return PromptMessage.withUserRole(new TextContent("Hello"));
    }

    @Prompt(description = "Prompt that returns two messages")
    List<PromptMessage> multi() {
        return List.of(
                PromptMessage.withUserRole(new TextContent("first")),
                PromptMessage.withUserRole(new TextContent("second"))
        );
    }

    @Prompt(description = "Parametrized prompt")
    PromptMessage parametrized(@PromptArg(description = "The name") String name) {
        return PromptMessage.withUserRole(new TextContent("Hello " + name));
    }

    @Prompt(description = "Prompt that returns an image")
    PromptMessage image() {
        return PromptMessage.withUserRole(new ImageContent("aaa", "image/png"));
    }

    @Prompt(description = "Prompt that returns an embedded binary resource")
    PromptMessage embeddedBinaryResource() {
        ResourceContents blob = new BlobResourceContents("file:///embedded-blob", "aaaaa", "application/octet-stream");
        Content content = new EmbeddedResource(blob);
        return new PromptMessage(Role.USER, content);
    }

}
