package dev.langchain4j.mcp.client.auth;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.Experimental;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.mcp.client.transport.McpJson;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link McpAuthProvider} that obtains an access token from an OAuth 2.0 authorization server
 * with the {@code client_credentials} grant (RFC 6749 section 4.4) and sends it as a bearer token.
 *
 * <p>This is the flow for an MCP client that acts on its own behalf rather than on behalf of a
 * user: a backend service calling a protected MCP server. The token is cached and reused until
 * shortly before it expires. When the MCP server rejects a request with {@code 401}, the cached
 * token is discarded and a new one is obtained; when it rejects a request with
 * {@code 403 insufficient_scope}, the scopes named in the challenge are added to the ones
 * requested from the authorization server, as the step-up flow of the MCP authorization
 * specification describes.
 *
 * <p><b>Discovery.</b> When no {@code tokenEndpoint} is configured, the provider discovers the
 * authorization server the way the MCP authorization specification prescribes: the first request
 * is sent without credentials, the server answers {@code 401} with a {@code WWW-Authenticate}
 * challenge, and the provider follows it (or the well-known URIs) to the server's Protected
 * Resource Metadata (RFC 9728) and from there to the authorization server metadata (RFC 8414 or
 * OpenID Connect Discovery), see {@link McpAuthorizationDiscovery}. The token endpoint, the
 * {@code resource} indicator and, unless configured, the scopes and the client authentication
 * method are taken from what was discovered: scopes from the challenge first, then from the
 * resource metadata.
 *
 * <pre>{@code
 * // explicit configuration
 * McpAuthProvider auth = OAuth2ClientCredentialsAuthProvider.builder()
 *         .tokenEndpoint("https://auth.example.com/oauth2/token")
 *         .clientId("my-agent")
 *         .clientSecret(System.getenv("MCP_CLIENT_SECRET"))
 *         .scopes("mcp:tools")
 *         .resource("https://mcp.example.com/mcp")
 *         .build();
 *
 * // discovery: only the client credentials are needed
 * McpAuthProvider auth = OAuth2ClientCredentialsAuthProvider.builder()
 *         .clientId("my-agent")
 *         .clientSecret(System.getenv("MCP_CLIENT_SECRET"))
 *         .build();
 *
 * McpTransport transport = StreamableHttpMcpTransport.builder()
 *         .url("https://mcp.example.com/mcp")
 *         .authProvider(auth)
 *         .build();
 * }</pre>
 */
@Experimental
public class OAuth2ClientCredentialsAuthProvider implements McpAuthProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth2ClientCredentialsAuthProvider.class);

    private static final String GRANT_TYPE = "client_credentials";

    /**
     * How the client authenticates to the token endpoint (RFC 6749 section 2.3.1).
     */
    public enum ClientAuthenticationMethod {
        /** HTTP Basic authentication with the client id and secret; the default. */
        CLIENT_SECRET_BASIC("client_secret_basic"),
        /** {@code client_id} and {@code client_secret} as form parameters in the request body. */
        CLIENT_SECRET_POST("client_secret_post");

        private final String metadataName;

        ClientAuthenticationMethod(String metadataName) {
            this.metadataName = metadataName;
        }

        /**
         * The name of this method in {@code token_endpoint_auth_methods_supported}.
         */
        public String metadataName() {
            return metadataName;
        }
    }

    private record Token(String value, @Nullable Instant expiresAt) {}

    /**
     * Where and how tokens are obtained: configured up front, or discovered from a challenge.
     */
    private record Endpoint(
            URI tokenEndpoint,
            @Nullable String resource,
            ClientAuthenticationMethod clientAuthenticationMethod,
            @Nullable String resourceMetadataUrl) {}

    private final @Nullable URI configuredTokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final @Nullable ClientAuthenticationMethod configuredClientAuthenticationMethod;
    private final @Nullable String configuredResource;
    private final Map<String, String> additionalParameters;
    private final Duration expirationSkew;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final Clock clock;
    private final McpAuthorizationDiscovery discovery;
    private final Set<String> allowedIssuers;

    private final ReentrantLock lock = new ReentrantLock();
    private final Set<String> scopes;
    private volatile @Nullable Endpoint endpoint;
    private volatile @Nullable Token token;

    private OAuth2ClientCredentialsAuthProvider(Builder builder) {
        this.configuredTokenEndpoint = builder.tokenEndpoint == null
                ? null
                : URI.create(ensureNotBlank(builder.tokenEndpoint, "tokenEndpoint"));
        this.clientId = ensureNotBlank(builder.clientId, "clientId");
        this.clientSecret = ensureNotBlank(builder.clientSecret, "clientSecret");
        this.configuredClientAuthenticationMethod = builder.clientAuthenticationMethod;
        this.configuredResource = builder.resource;
        this.additionalParameters = Map.copyOf(builder.additionalParameters);
        this.expirationSkew = getOrDefault(builder.expirationSkew, Duration.ofSeconds(30));
        this.timeout = getOrDefault(builder.timeout, Duration.ofSeconds(30));
        this.clock = getOrDefault(builder.clock, Clock.systemUTC());
        this.scopes = new LinkedHashSet<>(builder.scopes);
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.allowedIssuers = Set.copyOf(builder.allowedIssuers);
        if (builder.discovery != null) {
            this.discovery = builder.discovery;
        } else {
            // share an explicitly configured client (proxy, TLS, ...); otherwise let discovery build its
            // own, which follows redirects, unlike the one used for the token endpoint
            McpAuthorizationDiscovery.Builder discoveryBuilder =
                    McpAuthorizationDiscovery.builder().timeout(this.timeout);
            if (builder.httpClient != null) {
                discoveryBuilder.httpClient(builder.httpClient);
            }
            if (!this.allowedIssuers.isEmpty()) {
                discoveryBuilder.allowedIssuers(this.allowedIssuers);
            }
            this.discovery = discoveryBuilder.build();
        }
        if (configuredTokenEndpoint != null) {
            this.endpoint = new Endpoint(
                    configuredTokenEndpoint,
                    configuredResource,
                    getOrDefault(configuredClientAuthenticationMethod, ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
                    null);
        }
    }

    /**
     * Returns the bearer token, or {@code null} before the authorization server has been discovered:
     * the first request is then sent without credentials so that the MCP server's {@code 401}
     * challenge can tell the provider where to obtain a token.
     */
    @Override
    public @Nullable String getAuthorization(McpAuthRequest request) {
        Endpoint current = endpoint;
        if (current == null) {
            return null;
        }
        return "Bearer " + currentToken(current).value();
    }

    @Override
    public boolean onChallenge(McpAuthChallenge challenge) {
        lock.lock();
        try {
            if (challenge.statusCode() == 401) {
                if (configuredTokenEndpoint == null) {
                    discoverIfNeeded(challenge);
                }
                // scopes in the challenge are authoritative for the operation (union with what we asked for)
                scopes.addAll(challenge.scopes());
                // the token was rejected (missing, expired, revoked, wrong audience): obtain a fresh one
                token = null;
                return true;
            }
            if (challenge.isInsufficientScope() && !challenge.scopes().isEmpty()) {
                // step-up: request the union of what we asked for and what the server needs
                if (scopes.addAll(challenge.scopes())) {
                    token = null;
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Discards the cached token, so that the next request obtains a new one.
     */
    public void invalidate() {
        token = null;
    }

    /**
     * The token endpoint in use: the configured one, or the discovered one; {@code null} until
     * discovery has happened.
     */
    public @Nullable URI tokenEndpoint() {
        Endpoint current = endpoint;
        return current == null ? null : current.tokenEndpoint();
    }

    /**
     * The scopes currently requested from the authorization server: the configured ones, plus any
     * learned from challenges or resource metadata.
     */
    public List<String> requestedScopes() {
        lock.lock();
        try {
            return List.copyOf(scopes);
        } finally {
            lock.unlock();
        }
    }

    // called with the lock held
    private void discoverIfNeeded(McpAuthChallenge challenge) {
        Endpoint current = endpoint;
        String resourceMetadataUrl = challenge.resourceMetadata();
        boolean pointsElsewhere = current != null
                && resourceMetadataUrl != null
                && !Objects.equals(resourceMetadataUrl, current.resourceMetadataUrl());
        if (current != null && !pointsElsewhere) {
            return;
        }
        McpAuthorizationDiscovery.Result discovered = discovery.discover(challenge.uri(), resourceMetadataUrl);
        McpAuthorizationServerMetadata server = discovered.authorizationServer();
        if (!allowedIssuers.isEmpty() && !allowedIssuers.contains(server.issuer())) {
            // defence in depth: a caller-supplied McpAuthorizationDiscovery may not know the list
            throw new McpAuthorizationDiscoveryException("MCP server " + challenge.uri()
                    + " names authorization server " + server.issuer() + ", which is not one of the allowed issuers "
                    + allowedIssuers + "; refusing to request a token from it");
        }
        if (!server.supportsGrantType(GRANT_TYPE)) {
            throw new McpAuthorizationDiscoveryException("Authorization server " + server.issuer()
                    + " does not support the " + GRANT_TYPE + " grant (grant_types_supported: "
                    + server.grantTypesSupported() + ")");
        }
        ClientAuthenticationMethod method = selectClientAuthenticationMethod(server);
        String resource = configuredResource != null
                ? configuredResource
                : discovered.resource().resource();
        endpoint = new Endpoint(
                URI.create(server.tokenEndpoint()),
                resource,
                method,
                discovered.resourceMetadataUrl().toString());
        if (scopes.isEmpty() && challenge.scopes().isEmpty()) {
            // MCP scope selection strategy: challenge scope first, else scopes_supported of the resource
            scopes.addAll(discovered.resource().scopesSupported());
        }
        LOG.debug(
                "Using token endpoint {} of {} for MCP server {} (resource {}, client authentication {})",
                server.tokenEndpoint(),
                server.issuer(),
                challenge.uri(),
                resource,
                method.metadataName());
    }

    private ClientAuthenticationMethod selectClientAuthenticationMethod(McpAuthorizationServerMetadata server) {
        List<String> supported = server.tokenEndpointAuthMethodsSupported();
        if (configuredClientAuthenticationMethod != null) {
            if (!supported.isEmpty() && !supported.contains(configuredClientAuthenticationMethod.metadataName())) {
                LOG.warn(
                        "Authorization server {} advertises token endpoint authentication methods {}, "
                                + "not the configured {}",
                        server.issuer(),
                        supported,
                        configuredClientAuthenticationMethod.metadataName());
            }
            return configuredClientAuthenticationMethod;
        }
        // RFC 8414: an absent token_endpoint_auth_methods_supported means client_secret_basic
        if (supported.isEmpty() || supported.contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.metadataName())) {
            return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        }
        if (supported.contains(ClientAuthenticationMethod.CLIENT_SECRET_POST.metadataName())) {
            return ClientAuthenticationMethod.CLIENT_SECRET_POST;
        }
        throw new McpAuthorizationDiscoveryException("Authorization server " + server.issuer()
                + " supports none of the client authentication methods this provider implements "
                + "(token_endpoint_auth_methods_supported: " + supported + ")");
    }

    private Token currentToken(Endpoint endpoint) {
        Token current = token;
        if (current != null && !isExpired(current)) {
            return current;
        }
        lock.lock();
        try {
            current = token;
            if (current != null && !isExpired(current)) {
                return current;
            }
            Endpoint latest = getOrDefault(this.endpoint, endpoint);
            current = fetchToken(latest);
            token = current;
            return current;
        } finally {
            lock.unlock();
        }
    }

    private boolean isExpired(Token token) {
        return token.expiresAt() != null && !clock.instant().isBefore(token.expiresAt());
    }

    // called with the lock held
    private Token fetchToken(Endpoint endpoint) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", GRANT_TYPE);
        if (!scopes.isEmpty()) {
            form.put("scope", String.join(" ", scopes));
        }
        if (endpoint.resource() != null) {
            form.put("resource", endpoint.resource());
        }
        if (endpoint.clientAuthenticationMethod() == ClientAuthenticationMethod.CLIENT_SECRET_POST) {
            form.put("client_id", clientId);
            form.put("client_secret", clientSecret);
        }
        form.putAll(additionalParameters);

        URI tokenEndpoint = endpoint.tokenEndpoint();
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(tokenEndpoint)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)));
        if (endpoint.clientAuthenticationMethod() == ClientAuthenticationMethod.CLIENT_SECRET_BASIC) {
            String credentials = urlEncode(clientId) + ":" + urlEncode(clientSecret);
            request.header(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to obtain an access token from " + tokenEndpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while obtaining an access token from " + tokenEndpoint, e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpException(
                    response.statusCode(),
                    "Token endpoint " + tokenEndpoint + " returned status " + response.statusCode() + ": "
                            + response.body());
        }
        return parseToken(tokenEndpoint, response.body());
    }

    private Token parseToken(URI tokenEndpoint, String body) {
        JsonNode json;
        try {
            json = McpJson.parse(body);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Token endpoint " + tokenEndpoint + " returned a non-JSON response", e);
        }
        String accessToken = json.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Token endpoint " + tokenEndpoint + " returned no access_token");
        }
        String tokenType = json.path("token_type").asText(null);
        if (tokenType != null && !"bearer".equalsIgnoreCase(tokenType)) {
            throw new IllegalStateException("Token endpoint " + tokenEndpoint + " returned an unsupported token_type: "
                    + tokenType + " (only Bearer is supported)");
        }
        Instant expiresAt = null;
        JsonNode expiresIn = json.path("expires_in");
        // RFC 6749 says a number, but some servers send it as a string
        if (expiresIn.isNumber() || (expiresIn.isTextual() && expiresIn.asText().matches("\\d+"))) {
            Duration lifetime = Duration.ofSeconds(expiresIn.asLong());
            // never let the skew eat the whole lifetime, or every request would fetch a token
            Duration skew =
                    expirationSkew.compareTo(lifetime.dividedBy(2)) > 0 ? lifetime.dividedBy(2) : expirationSkew;
            expiresAt = clock.instant().plus(lifetime).minus(skew);
        }
        LOG.debug("Obtained an access token from {} (expires at {})", tokenEndpoint, expiresAt);
        return new Token(accessToken, expiresAt);
    }

    private static String encodeForm(Map<String, String> form) {
        List<String> pairs = new ArrayList<>(form.size());
        form.forEach((name, value) -> pairs.add(urlEncode(name) + "=" + urlEncode(value)));
        return String.join("&", pairs);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String tokenEndpoint;
        private String clientId;
        private String clientSecret;
        private ClientAuthenticationMethod clientAuthenticationMethod;
        private final List<String> scopes = new ArrayList<>();
        private String resource;
        private final Map<String, String> additionalParameters = new LinkedHashMap<>();
        private Duration expirationSkew;
        private Duration timeout;
        private HttpClient httpClient;
        private Clock clock;
        private McpAuthorizationDiscovery discovery;
        private final Set<String> allowedIssuers = new LinkedHashSet<>();

        /**
         * The token endpoint of the authorization server. Optional: when absent, the provider
         * discovers it from the MCP server's {@code 401} challenge and metadata, see
         * {@link McpAuthorizationDiscovery}.
         */
        public Builder tokenEndpoint(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
            return this;
        }

        /**
         * The OAuth 2.0 client identifier. Required.
         */
        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * The OAuth 2.0 client secret. Required.
         */
        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        /**
         * How to authenticate to the token endpoint. Defaults to
         * {@link ClientAuthenticationMethod#CLIENT_SECRET_BASIC}, unless the discovered
         * authorization server metadata advertises only {@code client_secret_post}.
         */
        public Builder clientAuthenticationMethod(ClientAuthenticationMethod clientAuthenticationMethod) {
            this.clientAuthenticationMethod = clientAuthenticationMethod;
            return this;
        }

        /**
         * The scopes to request. Optional: when absent, the scopes of the MCP server's challenge
         * are used, else the {@code scopes_supported} of its resource metadata. Scopes named in
         * {@code insufficient_scope} challenges are added automatically in any case.
         */
        public Builder scopes(List<String> scopes) {
            this.scopes.clear();
            this.scopes.addAll(ensureNotNull(scopes, "scopes"));
            return this;
        }

        /**
         * The scopes to request. Optional.
         */
        public Builder scopes(String... scopes) {
            return scopes(List.of(scopes));
        }

        /**
         * The RFC 8707 {@code resource} indicator: the canonical URI of the MCP server the token
         * is for. Optional: when absent and discovery is used, the {@code resource} of the MCP
         * server's resource metadata is sent. The MCP authorization specification requires
         * clients to send it.
         */
        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        /**
         * Extra form parameters to send with the token request, for example {@code audience}
         * for authorization servers that use it instead of {@code resource}. Optional.
         */
        public Builder additionalParameter(String name, String value) {
            this.additionalParameters.put(ensureNotBlank(name, "name"), ensureNotNull(value, "value"));
            return this;
        }

        /**
         * How long before its {@code expires_in} a token is treated as expired, to absorb clock
         * differences and request latency; capped at half the token lifetime. Defaults to 30 seconds.
         */
        public Builder expirationSkew(Duration expirationSkew) {
            this.expirationSkew = expirationSkew;
            return this;
        }

        /**
         * The timeout for requests to the token endpoint. Defaults to 30 seconds.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * The HTTP client used to call the token endpoint (and, unless a {@link #discovery} is
         * given, to fetch metadata). Optional; a default one that does not follow redirects is
         * created otherwise, while discovery then gets its own that does. Provide one to configure
         * proxies, TLS or an executor.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Restricts the authorization servers a token may be requested from to the given issuer
         * identifiers.
         * <p>
         * With discovery, the authorization server is whatever the MCP server's protected resource
         * metadata names, and this provider presents its client credentials to that server's token
         * endpoint. An application that does not trust the MCP server to name its authorization
         * server can list the issuers it expects here; any other issuer is refused, and it is never
         * contacted when this provider performs the discovery itself. Has no effect when
         * {@link #tokenEndpoint(String)} is configured. By default no restriction is applied.
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
         * Restricts the authorization servers a token may be requested from to the given issuer
         * identifiers.
         *
         * @param allowedIssuers the issuer identifiers to accept, compared as exact strings
         * @return {@code this}
         * @see #allowedIssuers(Collection)
         */
        public Builder allowedIssuers(String... allowedIssuers) {
            return allowedIssuers(List.of(allowedIssuers));
        }

        /**
         * The discovery used when no {@code tokenEndpoint} is configured. Optional; a default one
         * sharing this provider's HTTP client and timeout is created otherwise.
         */
        public Builder discovery(McpAuthorizationDiscovery discovery) {
            this.discovery = discovery;
            return this;
        }

        /**
         * The clock used to decide whether a token has expired. Intended for tests.
         */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public OAuth2ClientCredentialsAuthProvider build() {
            return new OAuth2ClientCredentialsAuthProvider(this);
        }
    }
}
