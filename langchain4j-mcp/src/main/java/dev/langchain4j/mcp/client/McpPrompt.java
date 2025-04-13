package dev.langchain4j.mcp.client;

import java.util.List;

/**
 * The 'Prompt' object from the MCP protocol schema.
 * It describes a declaration of a prompt, not its actual contents.
 * It contains a name, description and a list of arguments relevant for rendering an instance of the prompt.
 */
public record McpPrompt(String name, String description, List<McpPromptArgument> arguments) {}
