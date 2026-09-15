package dev.langchain4j.model.bedrock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;

/**
 * Deterministic coverage of {@link BedrockChatModel#doChatAsync} - a provider on its <b>own SDK</b> (AWS SDK v2),
 * not our {@code HttpClient}. It composes over the SDK's native async client ({@link BedrockRuntimeAsyncClient},
 * which returns a {@link CompletableFuture}) and reuses the shared non-blocking retry with Bedrock's own
 * {@code ExceptionMapper} - showing the async design is transport-agnostic.
 */
class BedrockChatModelAsyncRetryTest {

    private static ChatModel model(BedrockRuntimeAsyncClient asyncClient, int maxRetries) {
        return BedrockChatModel.builder()
                .modelId("anthropic.claude-3-5-sonnet-20240620-v1:0")
                .region(Region.US_EAST_1)
                .client(mock(BedrockRuntimeClient.class)) // avoid creating a real sync AWS client
                .asyncClient(asyncClient)
                .maxRetries(maxRetries)
                .build();
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }

    @Test
    void chatAsync_retries_a_retriable_failure_over_the_native_aws_async_client() {
        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        when(asyncClient.converse(any(ConverseRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("transient network error")));

        ChatModel model = model(asyncClient, 2);

        assertThatThrownBy(() -> model.chatAsync(request()).get(30, SECONDS))
                .hasMessageContaining("transient network error");
        // maxRetries(2) -> the AWS SDK async client is invoked 3 times, proving the retry runs on the SDK path
        verify(asyncClient, times(3)).converse(any(ConverseRequest.class));
    }

    @Test
    void chatAsync_does_not_retry_a_non_retriable_failure() {
        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        when(asyncClient.converse(any(ConverseRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        SdkServiceException.builder().statusCode(400).message("bad request").build()));

        ChatModel model = model(asyncClient, 5);

        // Bedrock's own ExceptionMapper maps a 400 to InvalidRequestException (NonRetriableException) - no retries
        assertThatThrownBy(() -> model.chatAsync(request()).get(10, SECONDS))
                .hasCauseInstanceOf(InvalidRequestException.class);
        verify(asyncClient, times(1)).converse(any(ConverseRequest.class));
    }
}
