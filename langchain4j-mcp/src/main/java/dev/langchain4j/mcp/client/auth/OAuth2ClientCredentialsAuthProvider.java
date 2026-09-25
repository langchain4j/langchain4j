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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <p>The {@code resource} parameter (RFC 8707) should be set to the canonical URI of the MCP
 * server so that the authorization server issues a token bound to it; the MCP authorization
 * specification requires clients to send it.
 *
 * <pre>{@code
 * McpAuthProvider auth = OAuth2ClientCredentialsAuthProvider.builder()
 *         .tokenEndpoint("https://auth.example.com/oauth2/token")
 *         .clientId("my-agent")
 *         .clientSecret(System.getenv("MCP_CLIENT_SECRET"))
 *         .scopes("mcp:tools")
 *         .resource("https://mcp.example.com/mcp")
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

    /**
     * How the client authenticates to the token endpoint (RFC 6749 section 2.3.1).
     */
    public enum ClientAuthenticationMethod {
        /** HTTP Basic authentication with the client id and secret; the default. */
        CLIENT_SECRET_BASIC,
        /** {@code client_id} and {@code client_secret} as form parameters in the request body. */
        CLIENT_SECRET_POST
    }

    private record Token(String value, @Nullable Instant expiresAt) {}

    private final URI tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final ClientAuthenticationMethod clientAuthenticationMethod;
    private final @Nullable String resource;
    private final Map<String, String> additionalParameters;
    private final Duration expirationSkew;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final Clock clock;

    private final ReentrantLock lock = new ReentrantLock();
    private final Set<String> scopes;
    private volatile @Nullable Token token;

    private OAuth2ClientCredentialsAuthProvider(Builder builder) {
        this.tokenEndpoint = URI.create(ensureNotBlank(builder.tokenEndpoint, "tokenEndpoint"));
        this.clientId = ensureNotBlank(builder.clientId, "clientId");
        this.clientSecret = ensureNotBlank(builder.clientSecret, "clientSecret");
        this.clientAuthenticationMethod =
                getOrDefault(builder.clientAuthenticationMethod, ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        this.resource = builder.resource;
        this.additionalParameters = Map.copyOf(builder.additionalParameters);
        this.expirationSkew = getOrDefault(builder.expirationSkew, Duration.ofSeconds(30));
        this.timeout = getOrDefault(builder.timeout, Duration.ofSeconds(30));
        this.clock = getOrDefault(builder.clock, Clock.systemUTC());
        this.scopes = new LinkedHashSet<>(builder.scopes);
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public String getAuthorization(McpAuthRequest request) {
        return "Bearer " + currentToken().value();
    }

    @Override
    public boolean onChallenge(McpAuthChallenge challenge) {
        lock.lock();
        try {
            if (challenge.statusCode() == 401) {
                // the token was rejected (expired, revoked, wrong audience): obtain a fresh one
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

    private Token currentToken() {
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
            current = fetchToken();
            token = current;
            return current;
        } finally {
            lock.unlock();
        }
    }

    private boolean isExpired(Token token) {
        return token.expiresAt() != null && !clock.instant().isBefore(token.expiresAt());
    }

    private Token fetchToken() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        if (!scopes.isEmpty()) {
            form.put("scope", String.join(" ", scopes));
        }
        if (resource != null) {
            form.put("resource", resource);
        }
        if (clientAuthenticationMethod == ClientAuthenticationMethod.CLIENT_SECRET_POST) {
            form.put("client_id", clientId);
            form.put("client_secret", clientSecret);
        }
        form.putAll(additionalParameters);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(tokenEndpoint)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)));
        if (clientAuthenticationMethod == ClientAuthenticationMethod.CLIENT_SECRET_BASIC) {
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
        return parseToken(response.body());
    }

    private Token parseToken(String body) {
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

        /**
         * The token endpoint of the authorization server. Required.
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
         * {@link ClientAuthenticationMethod#CLIENT_SECRET_BASIC}.
         */
        public Builder clientAuthenticationMethod(ClientAuthenticationMethod clientAuthenticationMethod) {
            this.clientAuthenticationMethod = clientAuthenticationMethod;
            return this;
        }

        /**
         * The scopes to request. Optional; scopes named in {@code insufficient_scope} challenges
         * are added to these automatically.
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
         * is for. Optional, but the MCP authorization specification requires clients to send it.
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
         * The HTTP client used to call the token endpoint. Optional; a default one is created
         * otherwise. Provide one to configure proxies, TLS or an executor.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
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
