package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;

class BedrockStreamingChatModelTest {

    @Test
    void should_fail_instantiation_with_both_client_and_credentialprovider() {
        final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient =
                BedrockRuntimeAsyncClient.builder().region(Region.US_EAST_1).build();
        final DefaultCredentialsProvider defaultCredentialsProvider =
                DefaultCredentialsProvider.builder().build();
        final RuntimeException runtimeException =
                assertThrows(RuntimeException.class, () -> BedrockStreamingChatModel.builder()
                        .modelId("us.amazon.nova-lite-v1:0")
                        .client(bedrockRuntimeAsyncClient)
                        .awsCredentialsProvider(defaultCredentialsProvider)
                        .build());
        assertThat(runtimeException.getMessage())
                .isEqualTo(
                        "You must provide either a BedrockRuntimeAsyncClient or a combination of region, awsCredentialsProvider, timeout, logRequests, and logResponses — not both. Providing both may lead to inconsistent behavior.");
    }
}
