package dev.langchain4j.mcp.client.auth;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers the authorization server of an MCP server, following the MCP authorization
 * specification:
 *
 * <ol>
 *   <li>Locate the server's Protected Resource Metadata (RFC 9728): at the {@code resource_metadata}
 *       URL of the {@code WWW-Authenticate} challenge when the server sent one, otherwise at the
 *       well-known URI that includes the path of the MCP endpoint
 *       ({@code https://host/.well-known/oauth-protected-resource/path}) and then at the root
 *       ({@code https://host/.well-known/oauth-protected-resource}).
 *   <li>Check that the metadata describes the MCP server it was fetched for.
 *   <li>For each listed authorization server, fetch its metadata (RFC 8414 or OpenID Connect
 *       Discovery), trying the well-known URIs in the order the specification prescribes for
 *       issuers with and without a path component, and use the first that resolves.
 *   <li>Reject a metadata document whose {@code issuer} is not identical to the issuer it was
 *       fetched for (RFC 8414 section 3.3), and any authorization server that is not served over
 *       HTTPS, except on loopback addresses used during development. The resource metadata itself
 *       may be served over plain HTTP only from the origin of the MCP server (an internal server
 *       reached over HTTP publishes its metadata the same way).
 * </ol>
 *
 * <p>Instances are thread-safe and stateless; callers cache the result.
 */
@Experimental
public class McpAuthorizationDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(McpAuthorizationDiscovery.class);

    private static final String PROTECTED_RESOURCE_WELL_KNOWN = "/.well-known/oauth-protected-resource";
    private static final String OAUTH_SERVER_WELL_KNOWN = "/.well-known/oauth-authorization-server";
    private static final String OPENID_WELL_KNOWN = "/.well-known/openid-configuration";

    /**
     * What discovery found.
     *
     * @param resource            the metadata of the MCP server (the protected resource)
     * @param authorizationServer the metadata of the authorization server to use
     * @param resourceMetadataUrl the URL the resource metadata was fetched from
     */
    public record Result(
            McpProtectedResourceMetadata resource,
            McpAuthorizationServerMetadata authorizationServer,
            URI resourceMetadataUrl) {}

    private final HttpClient httpClient;
    private final Duration timeout;
    private final int maxResponseBytes;
    private final boolean allowInsecureHttp;
    private final Set<String> allowedIssuers;

    private McpAuthorizationDiscovery(Builder builder) {
        this.timeout = getOrDefault(builder.timeout, Duration.ofSeconds(10));
        this.maxResponseBytes = getOrDefault(builder.maxResponseBytes, 1024 * 1024);
        this.allowInsecureHttp = builder.allowInsecureHttp;
        this.allowedIssuers = Set.copyOf(builder.allowedIssuers);
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(this.timeout)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    /**
     * Discovers the authorization server for the MCP server at {@code mcpServerUri}.
     *
     * @param mcpServerUri        the MCP endpoint
     * @param resourceMetadataUrl the {@code resource_metadata} URL from the server's
     *                            {@code WWW-Authenticate} challenge, or {@code null} to fall back to
     *                            the well-known URIs
     * @throws McpAuthorizationDiscoveryException if no usable metadata can be found
     */
    public Result discover(URI mcpServerUri, @Nullable String resourceMetadataUrl) {
        ensureNotNull(mcpServerUri, "mcpServerUri");
        List<String> failures = new ArrayList<>();
        for (URI candidate : resourceMetadataCandidates(mcpServerUri, resourceMetadataUrl)) {
            // the resource metadata is part of the MCP server: it may share the server's (possibly
            // plain HTTP, internal) origin, but anywhere else it must be served over HTTPS
            if (!isSecure(candidate) && !sameOrigin(candidate, mcpServerUri)) {
                failures.add(candidate + " is not served over HTTPS and is not on the origin of the MCP server");
                continue;
            }
            String body = fetch(candidate, failures);
            if (body == null) {
                continue;
            }
            McpProtectedResourceMetadata resource;
            try {
                resource = McpProtectedResourceMetadata.fromJson(body);
            } catch (McpAuthorizationDiscoveryException e) {
                throw new McpAuthorizationDiscoveryException(candidate + ": " + e.getMessage(), e);
            }
            validateResource(resource, mcpServerUri, candidate);
            McpAuthorizationServerMetadata authorizationServer = discoverAuthorizationServer(resource, failures);
            LOG.debug(
                    "Discovered authorization server {} for MCP server {} via {}",
                    authorizationServer.issuer(),
                    mcpServerUri,
                    candidate);
            return new Result(resource, authorizationServer, candidate);
        }
        throw new McpAuthorizationDiscoveryException("Could not find the protected resource metadata of MCP server "
                + mcpServerUri + ": " + String.join("; ", failures));
    }

    /**
     * Fetches and validates the metadata of the given authorization server, trying the well-known
     * URIs in the order the MCP specification prescribes.
     *
     * @throws McpAuthorizationDiscoveryException if no document resolves or the document fails validation
     */
    public McpAuthorizationServerMetadata discoverAuthorizationServer(String issuer) {
        List<String> failures = new ArrayList<>();
        McpAuthorizationServerMetadata metadata = tryAuthorizationServer(issuer, failures);
        if (metadata == null) {
            throw new McpAuthorizationDiscoveryException("Could not find the metadata of authorization server " + issuer
                    + ": " + String.join("; ", failures));
        }
        return metadata;
    }

    private McpAuthorizationServerMetadata discoverAuthorizationServer(
            McpProtectedResourceMetadata resource, List<String> failures) {
        for (String issuer : resource.authorizationServers()) {
            McpAuthorizationServerMetadata metadata = tryAuthorizationServer(issuer, failures);
            if (metadata != null) {
                return metadata;
            }
        }
        throw new McpAuthorizationDiscoveryException("None of the authorization servers listed for "
                + resource.resource() + " could be resolved: " + String.join("; ", failures));
    }

    private @Nullable McpAuthorizationServerMetadata tryAuthorizationServer(String issuer, List<String> failures) {
        URI issuerUri;
        try {
            issuerUri = new URI(issuer);
        } catch (URISyntaxException e) {
            failures.add("authorization server '" + issuer + "' is not a valid URI");
            return null;
        }
        if (!isSecure(issuerUri)) {
            failures.add("authorization server " + issuer + " is not served over HTTPS");
            return null;
        }
        if (!isAllowedIssuer(issuer)) {
            // the MCP server names its authorization server: an application that does not trust it
            // to do so can restrict the issuers, and one it did not allow is never contacted
            failures.add("authorization server " + issuer + " is not one of the allowed issuers " + allowedIssuers);
            return null;
        }
        for (URI candidate : authorizationServerMetadataCandidates(issuerUri)) {
            String body = fetch(candidate, failures);
            if (body == null) {
                continue;
            }
            McpAuthorizationServerMetadata metadata;
            try {
                metadata = McpAuthorizationServerMetadata.fromJson(body);
            } catch (McpAuthorizationDiscoveryException e) {
                failures.add(candidate + ": " + e.getMessage());
                continue;
            }
            if (!metadata.issuer().equals(issuer)) {
                // RFC 8414 section 3.3: a mismatch means the document is not for this issuer, or is an attack
                throw new McpAuthorizationDiscoveryException("The metadata fetched from " + candidate
                        + " declares issuer '" + metadata.issuer() + "' but was fetched for issuer '" + issuer
                        + "'; refusing to use it");
            }
            if (!isSecure(toUri(metadata.tokenEndpoint(), candidate))) {
                throw new McpAuthorizationDiscoveryException(
                        "The token endpoint of " + issuer + " is not served over HTTPS: " + metadata.tokenEndpoint());
            }
            return metadata;
        }
        return null;
    }

    /**
     * The URIs to try for the protected resource metadata, in order.
     */
    static List<URI> resourceMetadataCandidates(URI mcpServerUri, @Nullable String resourceMetadataUrl) {
        if (resourceMetadataUrl != null && !resourceMetadataUrl.isBlank()) {
            try {
                return List.of(new URI(resourceMetadataUrl));
            } catch (URISyntaxException e) {
                throw new McpAuthorizationDiscoveryException(
                        "The resource_metadata URL of the WWW-Authenticate challenge is not a valid URI: "
                                + resourceMetadataUrl,
                        e);
            }
        }
        List<URI> candidates = new ArrayList<>(2);
        String path = trimTrailingSlash(mcpServerUri.getRawPath() == null ? "" : mcpServerUri.getRawPath());
        if (!path.isEmpty()) {
            candidates.add(origin(mcpServerUri).resolve(PROTECTED_RESOURCE_WELL_KNOWN + path));
        }
        candidates.add(origin(mcpServerUri).resolve(PROTECTED_RESOURCE_WELL_KNOWN));
        return candidates;
    }

    /**
     * The URIs to try for the metadata of an issuer, in the order the MCP specification prescribes.
     */
    static List<URI> authorizationServerMetadataCandidates(URI issuer) {
        String path = trimTrailingSlash(issuer.getRawPath() == null ? "" : issuer.getRawPath());
        URI origin = origin(issuer);
        if (path.isEmpty()) {
            return List.of(origin.resolve(OAUTH_SERVER_WELL_KNOWN), origin.resolve(OPENID_WELL_KNOWN));
        }
        return List.of(
                origin.resolve(OAUTH_SERVER_WELL_KNOWN + path),
                origin.resolve(OPENID_WELL_KNOWN + path),
                origin.resolve(path + OPENID_WELL_KNOWN));
    }

    /**
     * The canonical resource identifier of an MCP server (RFC 8707 section 2, as the MCP
     * specification applies it): lower-case scheme and host, no default port, no fragment, no
     * trailing slash.
     */
    public static String canonicalResource(URI mcpServerUri) {
        ensureNotNull(mcpServerUri, "mcpServerUri");
        StringBuilder canonical = new StringBuilder(origin(mcpServerUri).toString());
        String path = trimTrailingSlash(mcpServerUri.getRawPath() == null ? "" : mcpServerUri.getRawPath());
        canonical.append(path);
        if (mcpServerUri.getRawQuery() != null) {
            canonical.append('?').append(mcpServerUri.getRawQuery());
        }
        return canonical.toString();
    }

    private static void validateResource(
            McpProtectedResourceMetadata resource, URI mcpServerUri, URI resourceMetadataUrl) {
        URI declared;
        try {
            declared = new URI(resource.resource());
        } catch (URISyntaxException e) {
            throw new McpAuthorizationDiscoveryException("The protected resource metadata at " + resourceMetadataUrl
                    + " declares a 'resource' that is not a valid URI: " + resource.resource());
        }
        String declaredCanonical = canonicalResource(declared);
        String serverCanonical = canonicalResource(mcpServerUri);
        // RFC 9728 section 3.3: the declared resource must be the one the client is talking to. A
        // resource identifier that is a parent of the MCP endpoint (the server publishes one
        // document for all its endpoints) is accepted; anything else could redirect the client to
        // an authorization server chosen by an attacker.
        boolean sameResource = declaredCanonical.equals(serverCanonical);
        boolean parentOfEndpoint = serverCanonical.startsWith(declaredCanonical + "/");
        if (!sameResource && !parentOfEndpoint) {
            throw new McpAuthorizationDiscoveryException("The protected resource metadata at " + resourceMetadataUrl
                    + " is for resource '" + resource.resource() + "', not for the MCP server " + mcpServerUri
                    + "; refusing to use it");
        }
    }

    private @Nullable String fetch(URI uri, List<String> failures) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            failures.add(uri + ": " + e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpAuthorizationDiscoveryException("Interrupted while fetching " + uri, e);
        }
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                failures.add(uri + " returned status " + response.statusCode());
                return null;
            }
            byte[] bytes = body.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) {
                throw new McpAuthorizationDiscoveryException(
                        "The metadata document at " + uri + " exceeds " + maxResponseBytes + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            failures.add(uri + ": " + e);
            return null;
        }
    }

    /**
     * Whether {@code issuer} may be used: either no issuers were configured, or it is one of them.
     * The comparison is the simple string comparison RFC 8414 prescribes for issuer identifiers.
     *
     * @param issuer the issuer identifier from the protected resource metadata
     * @return whether the issuer may be used
     */
    public boolean isAllowedIssuer(String issuer) {
        return allowedIssuers.isEmpty() || allowedIssuers.contains(issuer);
    }

    /**
     * The issuers this instance accepts, empty when any issuer is accepted.
     *
     * @return the allowed issuer identifiers
     */
    public Set<String> allowedIssuers() {
        return allowedIssuers;
    }

    private boolean isSecure(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return true;
        }
        if (!"http".equals(scheme)) {
            return false;
        }
        return allowInsecureHttp || isLoopback(uri.getHost());
    }

    private static boolean sameOrigin(URI a, URI b) {
        try {
            return origin(a).equals(origin(b));
        } catch (McpAuthorizationDiscoveryException e) {
            return false;
        }
    }

    private static boolean isLoopback(@Nullable String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]") || h.equals("::1");
    }

    private static URI toUri(String value, URI reportedAt) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new McpAuthorizationDiscoveryException(
                    "The metadata at " + reportedAt + " contains an endpoint that is not a valid URI: " + value, e);
        }
    }

    private static URI origin(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (scheme.isEmpty() || host.isEmpty()) {
            throw new McpAuthorizationDiscoveryException("Not an absolute http(s) URI: " + uri);
        }
        int port = uri.getPort();
        boolean defaultPort =
                port == -1 || ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        String authority = defaultPort ? host : host + ":" + port;
        return URI.create(scheme + "://" + authority);
    }

    private static String trimTrailingSlash(String path) {
        String trimmed = path;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private HttpClient httpClient;
        private Duration timeout;
        private Integer maxResponseBytes;
        private boolean allowInsecureHttp;
        private final Set<String> allowedIssuers = new LinkedHashSet<>();

        /**
         * The HTTP client used to fetch metadata documents. Optional; a default one that follows
         * redirects (except from HTTPS to HTTP) is created otherwise.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * The timeout for each metadata request. Defaults to 10 seconds.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * The maximum size of a metadata document. Defaults to 1 MiB.
         */
        public Builder maxResponseBytes(int maxResponseBytes) {
            this.maxResponseBytes = maxResponseBytes;
            return this;
        }

        /**
         * Restricts the authorization servers that may be used to the given issuer identifiers.
         * <p>
         * The authorization server of an MCP server is whatever its protected resource metadata
         * names. An application that does not trust the MCP server to name it, for example because
         * the provider authenticates with a client secret that must not be presented anywhere else,
         * can list the issuers it expects here; any other issuer is refused and never contacted.
         * By default no restriction is applied.
         *
         * @param allowedIssuers the issuer identifiers to accept, compared as exact strings
         * @return {@code this}
         */
        public Builder allowedIssuers(Collection<String> allowedIssuers) {
            for (String issuer : ensureNotNull(allowedIssuers, "allowedIssuers")) {
                this.allowedIssuers.add(ensureNotBlank(issuer, "allowedIssuers entry"));
            }
            return this;
        }

        /**
         * Restricts the authorization servers that may be used to the given issuer identifiers.
         *
         * @param allowedIssuers the issuer identifiers to accept, compared as exact strings
         * @return {@code this}
         * @see #allowedIssuers(Collection)
         */
        public Builder allowedIssuers(String... allowedIssuers) {
            return allowedIssuers(List.of(allowedIssuers));
        }

        /**
         * Allows authorization servers and metadata documents served over plain HTTP on hosts other
         * than loopback. Defaults to {@code false}; the MCP specification requires HTTPS. Intended
         * for test environments only.
         */
        public Builder allowInsecureHttp(boolean allowInsecureHttp) {
            this.allowInsecureHttp = allowInsecureHttp;
            return this;
        }

        public McpAuthorizationDiscovery build() {
            return new McpAuthorizationDiscovery(this);
        }
    }
}
