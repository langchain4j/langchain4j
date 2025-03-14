package dev.langchain4j.mcp.client;

import java.util.List;

/**
 * Declaration of a prompt from an MCP server (not the actual contents).
 * It contains a name, description and a list of arguments relevant for rendering an instance of the prompt.
 */
public record PromptRef(String name, String description, List<PromptArg> arguments) {}
