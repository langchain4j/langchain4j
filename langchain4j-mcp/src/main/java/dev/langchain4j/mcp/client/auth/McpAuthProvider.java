package dev.langchain4j.mcp.client.auth;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Supplies credentials for HTTP-based MCP transports.
 *
 * <p>The transport asks the provider for the value of the {@code Authorization} header before every
 * HTTP request it makes to the MCP server. When the server rejects a request with {@code 401} or
 * {@code 403}, the transport hands the challenge to {@link #onChallenge(McpAuthChallenge)}; a
 * provider that can obtain fresh credentials returns {@code true} and the transport retries the
 * request once, asking for the {@code Authorization} value again.
 *
 * <p>Implementations must be thread-safe: the transport may call them concurrently.
 * {@link #getAuthorization(McpAuthRequest)} is called on the thread that sends the request and
 * may block (for example to obtain a token), so implementations should cache credentials and
 * only go to the network when they have none or they expired; {@link #onChallenge(McpAuthChallenge)}
 * is called on the transport's executor, off the HTTP client's threads.
 *
 * @see OAuth2ClientCredentialsAuthProvider
 */
@Experimental
public interface McpAuthProvider {

    /**
     * Returns the value of the {@code Authorization} header for the given request, including the
     * scheme (for example {@code "Bearer the_access_token"}), or {@code null} to send the request
     * without an {@code Authorization} header.
     */
    @Nullable
    String getAuthorization(McpAuthRequest request);

    /**
     * Called when the MCP server rejects a request with a {@code 401} or {@code 403} status.
     *
     * <p>Return {@code true} if the provider has (or can now obtain) credentials that are likely to
     * satisfy the challenge; the transport then retries the rejected request once, calling
     * {@link #getAuthorization(McpAuthRequest)} again. Return {@code false} to let the rejection
     * propagate to the caller. The default returns {@code false}.
     */
    default boolean onChallenge(McpAuthChallenge challenge) {
        return false;
    }

    /**
     * A provider that always sends the given bearer token.
     */
    static McpAuthProvider bearer(String token) {
        ensureNotBlank(token, "token");
        String authorization = "Bearer " + token;
        return request -> authorization;
    }

    /**
     * A provider that sends a bearer token obtained from the supplier for each request, for
     * example a token propagated from the session of the current user. A {@code null} from the
     * supplier sends the request without an {@code Authorization} header.
     */
    static McpAuthProvider bearer(Supplier<@Nullable String> tokenSupplier) {
        ensureNotNull(tokenSupplier, "tokenSupplier");
        return request -> {
            String token = tokenSupplier.get();
            return token == null ? null : "Bearer " + token;
        };
    }
}
