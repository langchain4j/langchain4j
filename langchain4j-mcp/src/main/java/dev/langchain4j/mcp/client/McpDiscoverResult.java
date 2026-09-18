package dev.langchain4j.mcp.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What an MCP server reports about itself when the client connects to it, using the
 * {@code server/discover} request of MCP protocol version 2026-07-28 and later.
 * It is delivered to {@link McpClientListener#afterServerDiscover(McpCallContext, McpDiscoverResult)}.
 *
 * @param supportedVersions the protocol versions the server can speak, never {@code null}
 * @param capabilities the optional protocol features the server offers, such as {@code tools}
 *                     or {@code resources}, as a tree of nested maps; never {@code null},
 *                     but empty for a server that offers none
 * @param serverInfo which server this is, if it says
 * @param instructions a hint from the server on how it is meant to be used, if it provides one
 * @param resultType how the server answered; {@code "complete"} for an ordinary answer
 */
public record McpDiscoverResult(
        List<String> supportedVersions,
        Map<String, Object> capabilities,
        @Nullable McpServerInfo serverInfo,
        @Nullable String instructions,
        @Nullable String resultType) {

    public McpDiscoverResult {
        supportedVersions =
                supportedVersions == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(supportedVersions));
        capabilities = capabilities == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(capabilities));
    }
}
