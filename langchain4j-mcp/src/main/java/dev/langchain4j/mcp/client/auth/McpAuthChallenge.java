package dev.langchain4j.mcp.client.auth;

import static java.util.Collections.unmodifiableMap;

import dev.langchain4j.Experimental;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An authorization challenge returned by the MCP server: the {@code 401} or {@code 403} status and
 * the parameters of the {@code Bearer} {@code WWW-Authenticate} header (RFC 6750 section 3), if any.
 *
 * <p>The MCP authorization specification uses these parameters to point the client at the
 * protected resource metadata of the server ({@code resource_metadata}), to tell it which scopes
 * the operation needs ({@code scope}) and to classify the failure ({@code error}, for example
 * {@code invalid_token} or {@code insufficient_scope}).
 *
 * @param statusCode the HTTP status of the rejected request, {@code 401} or {@code 403}
 * @param uri        the MCP server endpoint that rejected the request
 * @param scheme     the authentication scheme of the challenge, or {@code null} when the response
 *                   carried no {@code Bearer} {@code WWW-Authenticate} challenge
 * @param parameters the challenge parameters, keyed by lower-cased name; never {@code null}
 */
@Experimental
public record McpAuthChallenge(
        int statusCode, URI uri, @Nullable String scheme, Map<String, String> parameters) {

    public McpAuthChallenge {
        parameters = parameters == null ? Map.of() : unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * The URL of the OAuth 2.0 protected resource metadata of the server (RFC 9728), if advertised.
     */
    public @Nullable String resourceMetadata() {
        return parameters.get("resource_metadata");
    }

    /**
     * The scopes the server requires for the rejected operation, or an empty list if the
     * challenge did not carry a {@code scope} parameter.
     */
    public List<String> scopes() {
        String scope = parameters.get("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return List.of(scope.trim().split("\\s+"));
    }

    /**
     * The {@code error} parameter of the challenge, for example {@code invalid_token} or
     * {@code insufficient_scope}, or {@code null} if absent.
     */
    public @Nullable String error() {
        return parameters.get("error");
    }

    /**
     * Whether the challenge carries {@code error="insufficient_scope"}, meaning the credentials
     * were valid but do not cover the requested operation.
     */
    public boolean isInsufficientScope() {
        return "insufficient_scope".equalsIgnoreCase(error());
    }

    /**
     * Builds a challenge from the {@code WWW-Authenticate} header values of a rejected response.
     * When the header carries several challenges, the {@code Bearer} one is used; when it is absent
     * or carries no {@code Bearer} challenge, the returned challenge has a {@code null} scheme and
     * no parameters.
     */
    public static McpAuthChallenge parse(int statusCode, URI uri, List<String> wwwAuthenticateHeaders) {
        for (String header : wwwAuthenticateHeaders) {
            for (Challenge challenge : parseChallenges(header)) {
                if ("bearer".equalsIgnoreCase(challenge.scheme)) {
                    return new McpAuthChallenge(statusCode, uri, challenge.scheme, challenge.parameters);
                }
            }
        }
        return new McpAuthChallenge(statusCode, uri, null, Map.of());
    }

    private record Challenge(String scheme, Map<String, String> parameters) {}

    /**
     * Parses the challenge list of RFC 9110 section 11.6.1 far enough for the {@code Bearer}
     * challenge of RFC 6750: a scheme, then comma-separated {@code name=value} parameters whose
     * value is a token or a quoted string. Anything the grammar does not cover is skipped rather
     * than rejected, because the challenge only guides the client.
     */
    private static List<Challenge> parseChallenges(String header) {
        List<Challenge> challenges = new ArrayList<>();
        int i = 0;
        int length = header.length();
        while (i < length) {
            i = skipSeparators(header, i);
            int schemeStart = i;
            while (i < length && !isSeparator(header.charAt(i)) && header.charAt(i) != '=') {
                i++;
            }
            if (i == schemeStart) {
                i++;
                continue;
            }
            String scheme = header.substring(schemeStart, i);
            Map<String, String> parameters = new LinkedHashMap<>();
            while (true) {
                int mark = i;
                i = skipSeparators(header, i);
                int nameStart = i;
                while (i < length && !isSeparator(header.charAt(i)) && header.charAt(i) != '=') {
                    i++;
                }
                if (i == nameStart || i >= length || header.charAt(i) != '=') {
                    // not a parameter: either the end, or the scheme of the next challenge
                    i = mark;
                    break;
                }
                String name = header.substring(nameStart, i).toLowerCase(Locale.ROOT);
                i++; // '='
                String value;
                if (i < length && header.charAt(i) == '"') {
                    StringBuilder quoted = new StringBuilder();
                    i++;
                    while (i < length && header.charAt(i) != '"') {
                        if (header.charAt(i) == '\\' && i + 1 < length) {
                            i++;
                        }
                        quoted.append(header.charAt(i));
                        i++;
                    }
                    i++; // closing quote
                    value = quoted.toString();
                } else {
                    int valueStart = i;
                    while (i < length && !isSeparator(header.charAt(i))) {
                        i++;
                    }
                    value = header.substring(valueStart, i);
                }
                parameters.put(name, value);
            }
            challenges.add(new Challenge(scheme, parameters));
        }
        return challenges;
    }

    private static int skipSeparators(String header, int i) {
        while (i < header.length() && isSeparator(header.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isSeparator(char c) {
        return c == ',' || c == ' ' || c == '\t';
    }
}
