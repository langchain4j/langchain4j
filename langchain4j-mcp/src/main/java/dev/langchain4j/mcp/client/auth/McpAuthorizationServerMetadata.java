package dev.langchain4j.mcp.client.auth;

import static dev.langchain4j.mcp.client.auth.McpProtectedResourceMetadata.parseObject;
import static dev.langchain4j.mcp.client.auth.McpProtectedResourceMetadata.stringList;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.Experimental;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The metadata of an OAuth 2.0 authorization server, obtained through OAuth 2.0 Authorization
 * Server Metadata (RFC 8414) or OpenID Connect Discovery 1.0; the two formats share the fields
 * modelled here.
 *
 * @param issuer                            the issuer identifier; validated to be identical to the
 *                                          one the document was fetched for
 * @param tokenEndpoint                     the token endpoint
 * @param authorizationEndpoint             the authorization endpoint, absent for servers that only
 *                                          issue tokens to clients acting on their own behalf
 * @param registrationEndpoint              the dynamic client registration endpoint, if any
 * @param scopesSupported                   the scopes the server documents, may be empty
 * @param grantTypesSupported               the grant types the server documents; empty means the
 *                                          server did not say (RFC 8414 defaults it to
 *                                          {@code authorization_code} and {@code implicit})
 * @param tokenEndpointAuthMethodsSupported the client authentication methods of the token endpoint;
 *                                          empty means the server did not say (RFC 8414 defaults it
 *                                          to {@code client_secret_basic})
 * @param codeChallengeMethodsSupported     the PKCE methods the server documents; empty means PKCE
 *                                          support is not advertised
 */
@Experimental
public record McpAuthorizationServerMetadata(
        String issuer,
        String tokenEndpoint,
        @Nullable String authorizationEndpoint,
        @Nullable String registrationEndpoint,
        List<String> scopesSupported,
        List<String> grantTypesSupported,
        List<String> tokenEndpointAuthMethodsSupported,
        List<String> codeChallengeMethodsSupported) {

    public McpAuthorizationServerMetadata {
        scopesSupported = List.copyOf(scopesSupported);
        grantTypesSupported = List.copyOf(grantTypesSupported);
        tokenEndpointAuthMethodsSupported = List.copyOf(tokenEndpointAuthMethodsSupported);
        codeChallengeMethodsSupported = List.copyOf(codeChallengeMethodsSupported);
    }

    /**
     * Whether the server documents support for the given grant type, or documents nothing at all
     * (in which case the client can only try).
     */
    public boolean supportsGrantType(String grantType) {
        return grantTypesSupported.isEmpty() || grantTypesSupported.contains(grantType);
    }

    /**
     * Parses an authorization server metadata document. Unknown fields are ignored.
     *
     * @throws McpAuthorizationDiscoveryException if the document is not JSON or lacks
     *                                            {@code issuer} or {@code token_endpoint}
     */
    public static McpAuthorizationServerMetadata fromJson(String json) {
        JsonNode node = parseObject(json, "authorization server metadata");
        String issuer = node.path("issuer").asText(null);
        if (issuer == null || issuer.isBlank()) {
            throw new McpAuthorizationDiscoveryException("Authorization server metadata has no 'issuer'");
        }
        String tokenEndpoint = node.path("token_endpoint").asText(null);
        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            throw new McpAuthorizationDiscoveryException(
                    "Authorization server metadata of " + issuer + " has no 'token_endpoint'");
        }
        return new McpAuthorizationServerMetadata(
                issuer,
                tokenEndpoint,
                node.path("authorization_endpoint").asText(null),
                node.path("registration_endpoint").asText(null),
                stringList(node, "scopes_supported"),
                stringList(node, "grant_types_supported"),
                stringList(node, "token_endpoint_auth_methods_supported"),
                stringList(node, "code_challenge_methods_supported"));
    }
}
