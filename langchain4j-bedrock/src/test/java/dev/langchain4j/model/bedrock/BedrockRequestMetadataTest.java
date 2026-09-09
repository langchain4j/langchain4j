package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.AwsResponseMetadata;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

class BedrockRequestMetadataTest {

    @Test
    void should_add_default_request_metadata_to_converse_request() {
        BedrockRuntimeClient client = syncClient();
        BedrockChatModel model = BedrockChatModel.builder()
                .client(client)
                .modelId("test-model")
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .requestMetadata(Map.of("team", "platform"))
                        .build())
                .build();

        model.chat("hello");

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(requestCaptor.capture());
        assertThat(requestCaptor.getValue().requestMetadata()).isEqualTo(Map.of("team", "platform"));
    }

    @Test
    void should_add_default_request_metadata_to_converse_stream_request() {
        BedrockRuntimeAsyncClient client = asyncClient();
        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .client(client)
                .modelId("test-model")
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .requestMetadata(Map.of("team", "platform"))
                        .build())
                .build();

        model.chat("hello", mock(StreamingChatResponseHandler.class));

        ArgumentCaptor<ConverseStreamRequest> requestCaptor = ArgumentCaptor.forClass(ConverseStreamRequest.class);
        verify(client).converseStream(requestCaptor.capture(), any(ConverseStreamResponseHandler.class));
        assertThat(requestCaptor.getValue().requestMetadata()).isEqualTo(Map.of("team", "platform"));
    }

    @Test
    void should_override_default_request_metadata_for_a_single_request() {
        BedrockRuntimeClient client = syncClient();
        BedrockChatModel model = BedrockChatModel.builder()
                .client(client)
                .modelId("test-model")
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .requestMetadata(Map.of("team", "platform"))
                        .build())
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(BedrockChatRequestParameters.builder()
                        .requestMetadata(Map.of("traceId", "abc-123"))
                        .build())
                .build();

        model.chat(request);

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(requestCaptor.capture());
        assertThat(requestCaptor.getValue().requestMetadata()).isEqualTo(Map.of("traceId", "abc-123"));
    }

    @Test
    void should_not_add_request_metadata_when_not_configured() {
        BedrockRuntimeClient client = syncClient();
        BedrockChatModel model =
                BedrockChatModel.builder().client(client).modelId("test-model").build();

        model.chat("hello");

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(requestCaptor.capture());
        assertThat(requestCaptor.getValue().hasRequestMetadata()).isFalse();
    }

    private static BedrockRuntimeClient syncClient() {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        ConverseResponse response = successfulResponse();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);
        return client;
    }

    private static BedrockRuntimeAsyncClient asyncClient() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        return client;
    }

    private static ConverseResponse successfulResponse() {
        AwsResponseMetadata responseMetadata = new AwsResponseMetadata(Map.of("AWS_REQUEST_ID", "request-id")) {};

        ConverseResponse.Builder builder = ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("ok"))
                        .build()))
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder()
                        .inputTokens(1)
                        .outputTokens(1)
                        .totalTokens(2)
                        .build());
        builder.responseMetadata(responseMetadata);
        return builder.build();
    }
}
