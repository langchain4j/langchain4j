package dev.langchain4j.model.vertexai.anthropic.internal.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Basic validation of the {@link VertexAiAnthropicClient#resolveEndpoint(String)} behavior
 */
class VertexAiAnthropicClientTest {

    private static final String DELTA_PREFIX =
            "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"";
    private static final String DELTA_SUFFIX = "\"}}";

    @Test
    void should_resolve_global_endpoint_without_location_prefix() {
        assertThat(VertexAiAnthropicClient.resolveEndpoint("global")).isEqualTo("aiplatform.googleapis.com:443");
    }

    @Test
    void should_resolve_multi_region_endpoints() {
        assertThat(VertexAiAnthropicClient.resolveEndpoint("us")).isEqualTo("aiplatform.us.rep.googleapis.com:443");
        assertThat(VertexAiAnthropicClient.resolveEndpoint("eu")).isEqualTo("aiplatform.eu.rep.googleapis.com:443");
    }

    @Test
    void should_resolve_regional_endpoint() {
        assertThat(VertexAiAnthropicClient.resolveEndpoint("us-east5"))
                .isEqualTo("us-east5-aiplatform.googleapis.com:443");
        assertThat(VertexAiAnthropicClient.resolveEndpoint("europe-west1"))
                .isEqualTo("europe-west1-aiplatform.googleapis.com:443");
    }

    @Test
    void should_not_treat_a_region_starting_with_a_multi_region_name_as_multi_region() {
        assertThat(VertexAiAnthropicClient.resolveEndpoint("us-central1"))
                .isEqualTo("us-central1-aiplatform.googleapis.com:443");
        assertThat(VertexAiAnthropicClient.resolveEndpoint("europe-west4"))
                .isEqualTo("europe-west4-aiplatform.googleapis.com:443");
    }

    @Test
    void should_be_case_insensitive() {
        assertThat(VertexAiAnthropicClient.resolveEndpoint("GLOBAL")).isEqualTo("aiplatform.googleapis.com:443");
        assertThat(VertexAiAnthropicClient.resolveEndpoint("US")).isEqualTo("aiplatform.us.rep.googleapis.com:443");
        assertThat(VertexAiAnthropicClient.resolveEndpoint("US-East5"))
                .isEqualTo("us-east5-aiplatform.googleapis.com:443");
    }

    @Test
    void should_escape_backslashes_in_streamed_chunks() {
        // The backslash used to be copied verbatim, so "C:\t" arrived as "C:" plus a TAB: "\t" is a
        // valid JSON escape, which means the payload parsed without an error and the text was wrong
        assertThat(VertexAiAnthropicClient.toStreamChunks("C:\\t"))
                .containsExactly(DELTA_PREFIX + "C:\\\\t" + DELTA_SUFFIX);
    }

    @Test
    void should_escape_quotes_in_streamed_chunks() {
        assertThat(VertexAiAnthropicClient.toStreamChunks("say \"hi\""))
                .containsExactly(DELTA_PREFIX + "say \\\"hi\\\"" + DELTA_SUFFIX);
    }

    @Test
    void should_escape_control_characters_in_streamed_chunks() {
        // A raw control character inside a JSON string is invalid per RFC 8259
        assertThat(VertexAiAnthropicClient.toStreamChunks("a\tb\rc"))
                .containsExactly(DELTA_PREFIX + "a\\tb\\rc" + DELTA_SUFFIX);
    }

    @Test
    void should_not_split_a_surrogate_pair_when_chunking() {
        // The 10-char boundary falls inside the emoji, so the first chunk stops one char earlier
        // instead of shipping half of the pair (which would be encoded as a "?" on its own)
        List<String> chunks = VertexAiAnthropicClient.toStreamChunks("123456789\uD83D\uDE00 tail");

        assertThat(chunks)
                .containsExactly(
                        DELTA_PREFIX + "123456789" + DELTA_SUFFIX, DELTA_PREFIX + "\uD83D\uDE00 tail" + DELTA_SUFFIX);
    }
}
