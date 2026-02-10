package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockSupportedCapabilitiesTest {

    private static final String MODEL_ID = "us.amazon.nova-lite-v1:0";

    @Test
    void should_return_empty_supported_capabilities_by_default_for_chat_model() {
        BedrockChatModel model = BedrockChatModel.builder()
                .modelId(MODEL_ID)
                .client(mock(BedrockRuntimeClient.class))
                .build();

        assertThat(model.supportedCapabilities()).isEmpty();
    }

    @Test
    void should_return_configured_supported_capabilities_for_chat_model() {
        BedrockChatModel model = BedrockChatModel.builder()
                .modelId(MODEL_ID)
                .client(mock(BedrockRuntimeClient.class))
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertThat(model.supportedCapabilities()).containsExactly(RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @Test
    void should_return_empty_supported_capabilities_by_default_for_streaming_chat_model() {
        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId(MODEL_ID)
                .client(mock(BedrockRuntimeAsyncClient.class))
                .build();

        assertThat(model.supportedCapabilities()).isEmpty();
    }

    @Test
    void should_return_configured_supported_capabilities_for_streaming_chat_model() {
        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId(MODEL_ID)
                .client(mock(BedrockRuntimeAsyncClient.class))
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertThat(model.supportedCapabilities()).containsExactly(RESPONSE_FORMAT_JSON_SCHEMA);
    }
}
