package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockChatModelTest {

    @Test
    void should_fail_instantiation_when_both_client_and_credentialprovider_are_provided() {
        final BedrockRuntimeClient bedrockRuntimeClient =
                BedrockRuntimeClient.builder().region(Region.US_EAST_1).build();
        final DefaultCredentialsProvider awsCredentialsProvider =
                DefaultCredentialsProvider.builder().build();
        final RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> BedrockChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .client(bedrockRuntimeClient)
                .awsCredentialsProvider(awsCredentialsProvider)
                .build());
        assertThat(runtimeException.getMessage())
                .isEqualTo(
                        "You must provide either a BedrockRuntimeClient or a combination of region, awsCredentialsProvider, timeout, logRequests, and logResponses — not both. Providing both may lead to inconsistent behavior.");
    }
}
