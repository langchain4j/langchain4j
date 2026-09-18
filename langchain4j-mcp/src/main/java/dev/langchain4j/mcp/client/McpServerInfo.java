package dev.langchain4j.mcp.client;

import org.jspecify.annotations.Nullable;

/**
 * Identifies the MCP server: who it is and which version of it is running.
 * A server reports this while the client connects to it, and the client passes it on
 * through {@link McpDiscoverResult#serverInfo()}.
 *
 * @param name the server's programmatic name, for example {@code "filesystem-server"}
 * @param version the server's own version, unrelated to the MCP protocol version
 * @param title an optional human-readable name meant for display, if the server provides one
 */
public record McpServerInfo(String name, @Nullable String version, @Nullable String title) {}
