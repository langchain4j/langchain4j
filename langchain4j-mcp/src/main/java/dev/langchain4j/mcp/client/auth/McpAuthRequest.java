package dev.langchain4j.mcp.client.auth;

import dev.langchain4j.Experimental;
import dev.langchain4j.mcp.client.McpCallContext;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * The HTTP request an {@link McpAuthProvider} is asked to authorize.
 *
 * @param method      the HTTP method, for example {@code POST} for JSON-RPC messages or {@code GET}
 *                    for the subsidiary SSE channel
 * @param uri         the MCP server endpoint
 * @param callContext the MCP call this request carries, or {@code null} for requests that are not
 *                    tied to a single call (such as opening the subsidiary SSE channel)
 */
@Experimental
public record McpAuthRequest(
        String method, URI uri, @Nullable McpCallContext callContext) {}
