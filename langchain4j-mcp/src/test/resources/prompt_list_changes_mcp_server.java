///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.11.0

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;

import jakarta.inject.Inject;

// Server used for testing prompt list change notifications
public class prompt_list_changes_mcp_server {

    @Inject
    PromptManager promptManager;

    @Prompt(description = "A static prompt")
    PromptMessage staticPrompt() {
        return PromptMessage.withUserRole(new TextContent("Hello from static prompt"));
    }

    // Tool to register a new dynamic prompt (triggers list_changed notification)
    @Tool(description = "Registers a new dynamic prompt")
    public String registerNewPrompt() {
        promptManager.newPrompt("dynamicPrompt")
                .setDescription("A dynamically added prompt")
                .setHandler(
                        args -> PromptResponse.withMessages(
                                PromptMessage.withUserRole(
                                        new TextContent("Hello from dynamic prompt"))))
                .register();
        return "OK";
    }

}