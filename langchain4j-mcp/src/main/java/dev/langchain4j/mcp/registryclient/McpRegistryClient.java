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
     */
    McpGetServerResponse getServerDetails(String id);

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
