package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;

class BedrockStreamingChatModelTest {

    @Test
    void should_fail_instantiation_no_factory_found() {
        final RuntimeException runtimeException =
                assertThrows(RuntimeException.class, () -> BedrockStreamingChatModel.builder()
                        .modelId("us.amazon.nova-lite-v1:0")
                        .build());
        assertThat(runtimeException.getMessage()).isEqualTo("No BedrockRuntimeAsyncClientFactory implementation found");
    }

    // should fail because there is no HTTP client implementation in the classpath
    @Test
    void should_fail_instantiate_bedrockruntimeasyncclient_no_http_impl_found() {
        final RuntimeException runtimeException =
                assertThrows(RuntimeException.class, () -> BedrockRuntimeAsyncClient.builder()
                        .region(Region.EU_CENTRAL_1)
                        .build());
        assertThat(runtimeException.getMessage())
                .contains("Unable to load an HTTP implementation from any provider in the chain.");
    }
}
