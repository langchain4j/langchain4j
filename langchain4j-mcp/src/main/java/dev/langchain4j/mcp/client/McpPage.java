package dev.langchain4j.mcp.client;

import dev.langchain4j.Internal;
import java.util.List;

/**
 * One page of a paginated MCP list response.
 *
 * <p>The items and the cursor sit in the same response, so reading them together is what lets the
 * response be parsed once. Reading them separately meant parsing the whole document twice, and the
 * documents in question are lists - a server's entire tool catalogue, schemas included.
 *
 * @param items the page's entries
 * @param nextCursor the cursor for the following page, or null when this is the last one
 */
@Internal
record McpPage<T>(List<T> items, String nextCursor) {}
