package dev.langchain4j.model.vertexai.anthropic.internal.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Basic validation of the {@link VertexAiAnthropicClient#resolveEndpoint(String)} behavior
 */
class VertexAiAnthropicClientTest {

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
}
