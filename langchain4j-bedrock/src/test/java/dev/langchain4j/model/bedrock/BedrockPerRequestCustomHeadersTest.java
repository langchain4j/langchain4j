package dev.langchain4j.model.bedrock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.AwsResponseMetadata;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
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
import software.amazon.awssdk.services.s3.S3Client;

class BedrockPerRequestCustomHeadersTest {

    private static final String CONVERSE_RESPONSE_JSON =
            """
            {"output":{"message":{"role":"assistant","content":[{"text":"Hi"}]}},"stopReason":"end_turn",\
            "usage":{"inputTokens":1,"outputTokens":1,"totalTokens":2},"metrics":{"latencyMs":1}}""";

    private static final Map<String, String> MODEL_HEADERS = Map.of("X-Model-Header", "model", "X-Shared", "model");

    private static final ChatRequestParameters DEFAULT_PARAMETERS = ChatRequestParameters.builder()
            .httpParameters(HttpParameters.builder()
                    .customHeaders(Map.of("X-Default", "d", "X-Shared", "default"))
                    .build())
            .build();

    private static final ChatRequest REQUEST_WITH_SHARED_HEADER = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request"))
            .build();

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("x-shared", "request", "X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_send_per_request_headers_over_http() {

        // given
        CapturingSdkHttpClient httpClient = new CapturingSdkHttpClient(CONVERSE_RESPONSE_JSON);

        BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("access-key", "secret-key")))
                .httpClient(httpClient)
                .overrideConfiguration(config ->
                        config.addExecutionInterceptor(new BedrockCustomHeadersInterceptor(() -> MODEL_HEADERS)))
                .build();

        BedrockChatModel model = BedrockChatModel.builder()
                .client(client)
                .modelId("test-model")
                .maxRetries(0)
                .build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        assertThat(httpClient.requests).hasSize(1);
        Map<String, List<String>> headers = httpClient.requests.get(0).headers();
        assertThat(headers.get("X-Model-Header")).containsExactly("model");
        assertThat(headers.get("X-Shared")).containsExactly("request");
        assertThat(headers.get("X-Correlation-Id")).containsExactly("123");
    }

    @Test
    void chat_model_should_set_per_request_headers_on_converse_request() {

        // given
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(successfulResponse());

        BedrockChatModel model =
                BedrockChatModel.builder().client(client).modelId("test-model").build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(requestCaptor.capture());
        assertPerRequestHeaders(requestCaptor.getValue().overrideConfiguration().orElseThrow());
    }

    @Test
    void chat_model_should_set_per_request_headers_on_converse_request_async() throws Exception {

        // given
        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        when(asyncClient.converse(any(ConverseRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(successfulResponse()));

        BedrockChatModel model = BedrockChatModel.builder()
                .client(mock(BedrockRuntimeClient.class))
                .asyncClient(asyncClient)
                .modelId("test-model")
                .build();

        // when
        model.chatAsync(CHAT_REQUEST).get(5, SECONDS);

        // then
        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(asyncClient).converse(requestCaptor.capture());
        assertPerRequestHeaders(requestCaptor.getValue().overrideConfiguration().orElseThrow());
    }

    @Test
    void streaming_chat_model_should_set_per_request_headers_on_converse_stream_request() {

        // given
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .client(client)
                .modelId("test-model")
                .build();

        // when
        model.chat(CHAT_REQUEST, mock(StreamingChatResponseHandler.class));

        // then
        ArgumentCaptor<ConverseStreamRequest> requestCaptor = ArgumentCaptor.forClass(ConverseStreamRequest.class);
        verify(client).converseStream(requestCaptor.capture(), any(ConverseStreamResponseHandler.class));
        assertPerRequestHeaders(requestCaptor.getValue().overrideConfiguration().orElseThrow());
    }

    @Test
    void streaming_chat_model_publisher_should_set_per_request_headers_on_converse_stream_request() {

        // given
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .client(client)
                .modelId("test-model")
                .build();

        // when
        model.chat(CHAT_REQUEST).subscribe(new Flow.Subscriber<ChatModelStreamingEvent>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {}

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {}
        });

        // then
        ArgumentCaptor<ConverseStreamRequest> requestCaptor = ArgumentCaptor.forClass(ConverseStreamRequest.class);
        verify(client).converseStream(requestCaptor.capture(), any(ConverseStreamResponseHandler.class));
        assertPerRequestHeaders(requestCaptor.getValue().overrideConfiguration().orElseThrow());
    }

    @Test
    void chat_model_should_not_set_override_configuration_without_per_request_headers() {

        // given
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(successfulResponse());

        BedrockChatModel model =
                BedrockChatModel.builder().client(client).modelId("test-model").build();

        // when
        model.chat("Hello");

        // then
        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(requestCaptor.capture());
        assertThat(requestCaptor.getValue().overrideConfiguration()).isEmpty();
    }

    @Test
    void batch_chat_model_should_reject_per_request_headers() {

        // given
        BedrockClient bedrock = mock(BedrockClient.class);
        S3Client s3 = mock(S3Client.class);

        BedrockBatchChatModel model = BedrockBatchChatModel.builder()
                .bedrockClient(bedrock)
                .s3Client(s3)
                .modelId("test-model")
                .roleArn("arn:role")
                .outputS3Uri("s3://out-bucket/out")
                .inputS3Uri("s3://in-bucket/in")
                .build();

        // when-then
        assertThatThrownBy(() -> model.submit(new BatchRequest<>(List.of(CHAT_REQUEST))))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters");
        verifyNoInteractions(bedrock, s3);
    }

    @Test
    void per_request_authorization_header_should_not_replace_sigv4_signature() {

        // given
        CapturingSdkHttpClient httpClient = new CapturingSdkHttpClient(CONVERSE_RESPONSE_JSON);

        BedrockChatModel model = BedrockChatModel.builder()
                .client(signingClient(httpClient))
                .modelId("test-model")
                .maxRetries(0)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("Authorization", "Bearer request-token"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        assertThat(httpClient.requests).hasSize(1);
        List<String> authorization = httpClient.requests.get(0).headers().get("Authorization");
        assertThat(authorization).hasSize(1);
        assertThat(authorization.get(0)).startsWith("AWS4-HMAC-SHA256");
    }

    @Test
    void chat_model_should_send_headers_from_default_request_parameters_over_http() {

        // given
        CapturingSdkHttpClient httpClient = new CapturingSdkHttpClient(CONVERSE_RESPONSE_JSON);

        BedrockChatModel model = BedrockChatModel.builder()
                .client(signingClient(httpClient))
                .modelId("test-model")
                .maxRetries(0)
                .defaultRequestParameters(DEFAULT_PARAMETERS)
                .build();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER);

        // then
        assertThat(httpClient.requests).hasSize(1);
        Map<String, List<String>> headers = httpClient.requests.get(0).headers();
        assertThat(headers.get("X-Default")).containsExactly("d");
        assertThat(headers.get("X-Shared")).containsExactly("request");
    }

    @Test
    void streaming_chat_model_should_set_headers_from_default_request_parameters() {

        // given
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .client(client)
                .modelId("test-model")
                .defaultRequestParameters(DEFAULT_PARAMETERS)
                .build();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER, mock(StreamingChatResponseHandler.class));

        // then
        ArgumentCaptor<ConverseStreamRequest> requestCaptor = ArgumentCaptor.forClass(ConverseStreamRequest.class);
        verify(client).converseStream(requestCaptor.capture(), any(ConverseStreamResponseHandler.class));
        assertThat(requestCaptor.getValue().overrideConfiguration().orElseThrow().headers())
                .containsOnly(Map.entry("X-Default", List.of("d")), Map.entry("x-shared", List.of("request")));
    }

    @Test
    void batch_chat_model_should_reject_headers_in_default_request_parameters_at_build_time() {

        // given
        BedrockClient bedrock = mock(BedrockClient.class);
        S3Client s3 = mock(S3Client.class);

        // when-then
        assertThatThrownBy(() -> BedrockBatchChatModel.builder()
                        .bedrockClient(bedrock)
                        .s3Client(s3)
                        .modelId("test-model")
                        .roleArn("arn:role")
                        .outputS3Uri("s3://out-bucket/out")
                        .defaultRequestParameters(DEFAULT_PARAMETERS)
                        .build())
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters")
                .hasMessageContaining("batch");
        verifyNoInteractions(bedrock, s3);
    }

    private static BedrockRuntimeClient signingClient(SdkHttpClient httpClient) {
        return BedrockRuntimeClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("access-key", "secret-key")))
                .httpClient(httpClient)
                .build();
    }

    private static void assertPerRequestHeaders(AwsRequestOverrideConfiguration overrideConfiguration) {
        assertThat(overrideConfiguration.headers())
                .containsOnly(Map.entry("x-shared", List.of("request")), Map.entry("X-Correlation-Id", List.of("123")));
    }

    private static ConverseResponse successfulResponse() {
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
        builder.responseMetadata(new AwsResponseMetadata(Map.of("AWS_REQUEST_ID", "request-id")) {});
        return builder.build();
    }

    /**
     * Records every request that leaves the AWS SDK pipeline (after interceptors, header merging and signing)
     * and answers with a fixed JSON body.
     */
    private static class CapturingSdkHttpClient implements SdkHttpClient {

        private final String body;
        private final List<SdkHttpRequest> requests = new CopyOnWriteArrayList<>();

        CapturingSdkHttpClient(String body) {
            this.body = body;
        }

        @Override
        public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
            requests.add(request.httpRequest());
            return new ExecutableHttpRequest() {

                @Override
                public HttpExecuteResponse call() {
                    return HttpExecuteResponse.builder()
                            .response(SdkHttpResponse.builder()
                                    .statusCode(200)
                                    .putHeader("Content-Type", "application/json")
                                    .build())
                            .responseBody(AbortableInputStream.create(new ByteArrayInputStream(body.getBytes(UTF_8))))
                            .build();
                }

                @Override
                public void abort() {}
            };
        }

        @Override
        public void close() {}
    }

    @Test
    void per_request_user_agent_should_be_appended_to_the_sdk_user_agent_over_http() {

        // given
        CapturingSdkHttpClient httpClient = new CapturingSdkHttpClient(CONVERSE_RESPONSE_JSON);

        BedrockChatModel model = BedrockChatModel.builder()
                .client(signingClient(httpClient))
                .modelId("test-model")
                .maxRetries(0)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("user-agent", "google-adk/1.2.0 gl-java/21", "X-Shared", "request"))
                .build();

        // when
        model.chat("Hello");
        model.chat(chatRequest);

        // then
        assertThat(httpClient.requests).hasSize(2);
        List<String> defaultUserAgent = httpClient.requests.get(0).headers().get("User-Agent");
        assertThat(defaultUserAgent).hasSize(1);
        assertThat(defaultUserAgent.get(0)).startsWith("aws-sdk-java/");

        Map<String, List<String>> headers = httpClient.requests.get(1).headers();
        assertThat(headers.get("User-Agent")).containsExactly(defaultUserAgent.get(0) + " google-adk/1.2.0 gl-java/21");
        assertThat(headers.get("X-Shared")).containsExactly("request");
    }

    @Test
    void streaming_chat_model_should_send_per_request_user_agent_as_api_names() {

        // given
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .client(client)
                .modelId("test-model")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("User-Agent", "my-app/1.0"))
                .build();

        // when
        model.chat(chatRequest, mock(StreamingChatResponseHandler.class));

        // then
        ArgumentCaptor<ConverseStreamRequest> requestCaptor = ArgumentCaptor.forClass(ConverseStreamRequest.class);
        verify(client).converseStream(requestCaptor.capture(), any(ConverseStreamResponseHandler.class));
        AwsRequestOverrideConfiguration overrideConfiguration =
                requestCaptor.getValue().overrideConfiguration().orElseThrow();
        assertThat(overrideConfiguration.headers()).isEmpty();
        assertThat(overrideConfiguration.apiNames())
                .extracting(apiName -> apiName.name() + "/" + apiName.version())
                .containsExactly("my-app/1.0");
    }
}
