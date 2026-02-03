package dev.langchain4j.mcp.registryclient;

import dev.langchain4j.mcp.registryclient.model.McpGetServerResponse;
import dev.langchain4j.mcp.registryclient.model.McpRegistryHealth;
import dev.langchain4j.mcp.registryclient.model.McpRegistryPong;
import dev.langchain4j.mcp.registryclient.model.McpServerList;
import dev.langchain4j.mcp.registryclient.model.McpServerListRequest;

/**
 * The interface for talking to a MCP (sub)registry.
 * See <a href="https://registry.modelcontextprotocol.io/docs#/">official reference documentation</a> for more details about the API.
 * This interface closely mirrors the official API.
 */
public interface McpRegistryClient {

    /**
     * Obtains a list of MCP servers from the registry.
     */
    McpServerList listServers(McpServerListRequest request);

    /**
     * Obtains the details for a single MCP server denoted by its ID.
     * @deprecated This method is not supported on the official MCP registry anymore.
     */
    @Deprecated(forRemoval = true)
    McpGetServerResponse getServerDetails(String id);

    /**
     * Get detailed information about a specific version of an MCP server.
     * Use the special version 'latest' to get the latest version.
     */
    McpGetServerResponse getSpecificServerVersion(String serverName, String version);

    /**
     * Get all available versions for a specific MCP server
     */
    McpServerList getAllVersionsOfServer(String serverName);

    /**
     * Runs a health check against the MCP registry. If the registry is healthy,
     * the returned object's "status" field will be "ok".
     */
    McpRegistryHealth healthCheck();

    /**
     * Sends a ping message to the MCP registry. If the ping is successful,
     * the 'pong' field in the response object will contain 'true'.
     */
    McpRegistryPong ping();
}
