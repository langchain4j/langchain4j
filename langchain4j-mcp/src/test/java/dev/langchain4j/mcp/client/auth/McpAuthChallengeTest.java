package dev.langchain4j.mcp.client.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpAuthChallengeTest {

    private static final URI URI_ = URI.create("https://mcp.example.com/mcp");

    @Test
    void parses_bearer_challenge_with_quoted_parameters() {
        McpAuthChallenge challenge = McpAuthChallenge.parse(
                401,
                URI_,
                List.of("Bearer resource_metadata=\"https://mcp.example.com/.well-known/oauth-protected-resource\","
                        + " scope=\"files:read files:write\", error=\"invalid_token\""));

        assertThat(challenge.statusCode()).isEqualTo(401);
        assertThat(challenge.uri()).isEqualTo(URI_);
        assertThat(challenge.scheme()).isEqualTo("Bearer");
        assertThat(challenge.resourceMetadata())
                .isEqualTo("https://mcp.example.com/.well-known/oauth-protected-resource");
        assertThat(challenge.scopes()).containsExactly("files:read", "files:write");
        assertThat(challenge.error()).isEqualTo("invalid_token");
        assertThat(challenge.isInsufficientScope()).isFalse();
    }

    @Test
    void parses_unquoted_parameters_and_lower_cases_names() {
        McpAuthChallenge challenge =
                McpAuthChallenge.parse(403, URI_, List.of("bearer Error=insufficient_scope,Scope=a"));

        assertThat(challenge.scheme()).isEqualTo("bearer");
        assertThat(challenge.parameters())
                .containsExactly(Map.entry("error", "insufficient_scope"), Map.entry("scope", "a"));
        assertThat(challenge.isInsufficientScope()).isTrue();
    }

    @Test
    void unescapes_quoted_strings() {
        McpAuthChallenge challenge =
                McpAuthChallenge.parse(401, URI_, List.of("Bearer error_description=\"say \\\"hi\\\", please\""));

        assertThat(challenge.parameters()).containsEntry("error_description", "say \"hi\", please");
    }

    @Test
    void picks_the_bearer_challenge_among_several() {
        McpAuthChallenge challenge = McpAuthChallenge.parse(
                401,
                URI_,
                List.of("Basic realm=\"legacy\", Bearer realm=\"mcp\", scope=\"x\", Digest realm=\"other\""));

        assertThat(challenge.scheme()).isEqualTo("Bearer");
        assertThat(challenge.parameters()).containsExactly(Map.entry("realm", "mcp"), Map.entry("scope", "x"));
    }

    @Test
    void picks_the_bearer_challenge_among_several_header_values() {
        McpAuthChallenge challenge =
                McpAuthChallenge.parse(401, URI_, List.of("Basic realm=\"a\"", "Bearer scope=\"y\""));

        assertThat(challenge.scheme()).isEqualTo("Bearer");
        assertThat(challenge.scopes()).containsExactly("y");
    }

    @Test
    void bearer_without_parameters() {
        McpAuthChallenge challenge = McpAuthChallenge.parse(401, URI_, List.of("Bearer"));

        assertThat(challenge.scheme()).isEqualTo("Bearer");
        assertThat(challenge.parameters()).isEmpty();
        assertThat(challenge.scopes()).isEmpty();
        assertThat(challenge.resourceMetadata()).isNull();
        assertThat(challenge.error()).isNull();
    }

    @Test
    void no_header_or_no_bearer_challenge_yields_empty_challenge() {
        assertThat(McpAuthChallenge.parse(401, URI_, List.of()).scheme()).isNull();
        assertThat(McpAuthChallenge.parse(401, URI_, List.of()).parameters()).isEmpty();
        assertThat(McpAuthChallenge.parse(401, URI_, List.of("Basic realm=\"x\""))
                        .scheme())
                .isNull();
    }

    @Test
    void malformed_challenges_are_tolerated_instead_of_rejected() {
        for (String header :
                List.of("", " ", ",,, ,", "=\"x\"", "Bearer =", "Bearer a=", "Bearer ,a=1", "\"quoted\"")) {
            assertThat(McpAuthChallenge.parse(401, URI_, List.of(header)))
                    .as(header)
                    .isNotNull();
        }

        assertThat(McpAuthChallenge.parse(401, URI_, List.of("Bearer realm=\"unterminated"))
                        .parameters())
                .containsEntry("realm", "unterminated");
        assertThat(McpAuthChallenge.parse(401, URI_, List.of("Bearer a=1,,b=2")).parameters())
                .containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
        McpAuthChallenge trailingScheme = McpAuthChallenge.parse(401, URI_, List.of("Bearer realm=\"a\" Basic"));
        assertThat(trailingScheme.scheme()).isEqualTo("Bearer");
        assertThat(trailingScheme.parameters()).containsExactly(Map.entry("realm", "a"));
        assertThat(McpAuthChallenge.parse(403, URI_, List.of("Bearer scope=\"  a   b \""))
                        .scopes())
                .containsExactly("a", "b");
    }

    @Test
    void parameters_are_immutable_and_never_null() {
        McpAuthChallenge challenge = new McpAuthChallenge(401, URI_, "Bearer", null);

        assertThat(challenge.parameters()).isEmpty();
        assertThat(new McpAuthChallenge(401, URI_, "Bearer", Map.of("scope", "a")).parameters())
                .isUnmodifiable();
    }
}
