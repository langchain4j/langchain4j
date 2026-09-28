package dev.langchain4j.mcp.client.auth;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.Experimental;
import dev.langchain4j.mcp.client.transport.McpJson;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The OAuth 2.0 Protected Resource Metadata (RFC 9728) an MCP server publishes to tell clients
 * which authorization servers protect it. Only the fields the MCP client needs are modelled.
 *
 * @param resource             the resource identifier of the MCP server; the value clients send as
 *                             the RFC 8707 {@code resource} parameter
 * @param authorizationServers the issuer identifiers of the authorization servers that can issue
 *                             tokens for this resource, in the order the server lists them
 * @param scopesSupported      the scopes the server documents; the MCP specification treats them
 *                             as the minimal set needed for basic access
 * @param resourceName         a human-readable name of the resource, if provided
 */
@Experimental
public record McpProtectedResourceMetadata(
        String resource,
        List<String> authorizationServers,
        List<String> scopesSupported,
        @Nullable String resourceName) {

    public McpProtectedResourceMetadata {
        authorizationServers = List.copyOf(authorizationServers);
        scopesSupported = List.copyOf(scopesSupported);
    }

    /**
     * Parses a Protected Resource Metadata document. Unknown fields are ignored.
     *
     * @throws McpAuthorizationDiscoveryException if the document is not JSON, has no
     *                                            {@code resource}, or lists no authorization server
     */
    public static McpProtectedResourceMetadata fromJson(String json) {
        JsonNode node = parseObject(json, "protected resource metadata");
        String resource = node.path("resource").asText(null);
        if (resource == null || resource.isBlank()) {
            throw new McpAuthorizationDiscoveryException("Protected resource metadata has no 'resource'");
        }
        List<String> authorizationServers = stringList(node, "authorization_servers");
        if (authorizationServers.isEmpty()) {
            throw new McpAuthorizationDiscoveryException(
                    "Protected resource metadata for " + resource + " lists no 'authorization_servers'");
        }
        return new McpProtectedResourceMetadata(
                resource,
                authorizationServers,
                stringList(node, "scopes_supported"),
                node.path("resource_name").asText(null));
    }

    static JsonNode parseObject(String json, String what) {
        JsonNode node;
        try {
            node = McpJson.parse(json);
        } catch (RuntimeException e) {
            throw new McpAuthorizationDiscoveryException("The " + what + " document is not valid JSON", e);
        }
        if (node == null || !node.isObject()) {
            throw new McpAuthorizationDiscoveryException("The " + what + " document is not a JSON object");
        }
        return node;
    }

    static List<String> stringList(JsonNode node, String field) {
        JsonNode array = node.path(field);
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            if (element.isTextual() && !element.asText().isBlank()) {
                values.add(element.asText());
            }
        }
        return values;
    }
}
