package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.model.chat.request.ResponseFormat;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockDefaultRequestParametersTest {

    @Test
    void should_preserve_default_request_parameters_in_chat_model() {

        BedrockChatModel model = BedrockChatModel.builder()
                .client(mock(BedrockRuntimeClient.class))
                .modelId("test-model")
                .defaultRequestParameters(defaultRequestParameters())
                .build();

        assertDefaultRequestParametersPreserved(model.defaultRequestParameters());
    }

    @Test
    void should_preserve_default_request_parameters_in_streaming_chat_model() {

        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .client(mock(BedrockRuntimeAsyncClient.class))
                .modelId("test-model")
                .defaultRequestParameters(defaultRequestParameters())
                .build();

        assertDefaultRequestParametersPreserved(model.defaultRequestParameters());
    }

    private static BedrockChatRequestParameters defaultRequestParameters() {
        return BedrockChatRequestParameters.builder()
                .responseFormat(ResponseFormat.JSON)
                .serviceTier(BedrockServiceTier.PRIORITY)
                .build();
    }

    private static void assertDefaultRequestParametersPreserved(BedrockChatRequestParameters parameters) {
        assertThat(parameters.responseFormat()).isEqualTo(ResponseFormat.JSON);
        assertThat(parameters.serviceTier()).isEqualTo(BedrockServiceTier.PRIORITY);
    }
}
