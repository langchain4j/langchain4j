package dev.langchain4j.model.azure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpClientProvider;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AzureOpenAiPerRequestCustomHeadersTest {

    private static final String CHAT_COMPLETIONS_JSON =
            """
            {"id":"chatcmpl-1","object":"chat.completion","created":1721596428,"model":"gpt-4o-mini",\
            "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}],\
            "usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}""";

    private static final String CHAT_COMPLETIONS_STREAM =
            """
            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1721596428,"model":"gpt-4o-mini",\
            "choices":[{"index":0,"delta":{"role":"assistant","content":"Hello!"},"finish_reason":null}]}

            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1721596428,"model":"gpt-4o-mini",\
            "choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """;

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
    void chat_model_should_send_per_request_headers() {

        // given
        CapturingHttpClient httpClient = new CapturingHttpClient("application/json", CHAT_COMPLETIONS_JSON);

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .httpClientProvider(providerOf(httpClient))
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        ChatResponse chatResponse = model.chat(CHAT_REQUEST);

        // then
        assertThat(chatResponse.aiMessage().text()).isEqualTo("Hello!");
        assertPerRequestHeadersSent(httpClient.singleRequest().getHeaders());
    }

    @Test
    void per_request_header_should_replace_api_key_header() {

        // given
        CapturingHttpClient httpClient = new CapturingHttpClient("application/json", CHAT_COMPLETIONS_JSON);

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("model-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .httpClientProvider(providerOf(httpClient))
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("API-KEY", "request-key"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        assertThat(values(httpClient.singleRequest().getHeaders(), "api-key")).containsExactly("request-key");
    }

    @Test
    void chat_model_should_not_send_per_request_headers_of_previous_request() {

        // given
        CapturingHttpClient httpClient = new CapturingHttpClient("application/json", CHAT_COMPLETIONS_JSON);

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .httpClientProvider(providerOf(httpClient))
                .build();

        // when
        model.chat(CHAT_REQUEST);
        model.chat("Hello again");

        // then
        assertThat(httpClient.requests).hasSize(2);
        assertThat(httpClient.requests.get(1).getHeaders().get(HttpHeaderName.fromString("X-Correlation-Id")))
                .isNull();
    }

    @Test
    void streaming_chat_model_should_send_per_request_headers() throws Exception {

        // given
        CapturingHttpClient httpClient = new CapturingHttpClient("text/event-stream", CHAT_COMPLETIONS_STREAM);

        AzureOpenAiStreamingChatModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .httpClientProvider(providerOf(httpClient))
                .customHeaders(MODEL_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(CHAT_REQUEST, new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        // then
        assertThat(futureResponse.get(10, SECONDS).aiMessage().text()).isEqualTo("Hello!");
        assertPerRequestHeadersSent(httpClient.singleRequest().getHeaders());
    }

    @Test
    void chat_model_should_send_headers_from_default_request_parameters() {

        // given
        CapturingHttpClient httpClient = new CapturingHttpClient("application/json", CHAT_COMPLETIONS_JSON);

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .httpClientProvider(providerOf(httpClient))
                .defaultRequestParameters(DEFAULT_PARAMETERS)
                .build();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER);

        // then
        assertDefaultHeadersMergedWithRequestHeaders(httpClient.singleRequest().getHeaders());
    }

    @Test
    void streaming_chat_model_should_send_headers_from_default_request_parameters() throws Exception {

        // given
        CapturingHttpClient httpClient = new CapturingHttpClient("text/event-stream", CHAT_COMPLETIONS_STREAM);

        AzureOpenAiStreamingChatModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .httpClientProvider(providerOf(httpClient))
                .defaultRequestParameters(DEFAULT_PARAMETERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER, new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        // then
        futureResponse.get(10, SECONDS);
        assertDefaultHeadersMergedWithRequestHeaders(httpClient.singleRequest().getHeaders());
    }

    private static void assertDefaultHeadersMergedWithRequestHeaders(HttpHeaders headers) {
        assertThat(values(headers, "X-Default")).containsExactly("d");
        assertThat(values(headers, "X-Shared")).containsExactly("request");
    }

    private static HttpClientProvider providerOf(HttpClient httpClient) {
        HttpClientProvider httpClientProvider = mock(HttpClientProvider.class);
        when(httpClientProvider.createInstance(any())).thenReturn(httpClient);
        return httpClientProvider;
    }

    private static void assertPerRequestHeadersSent(HttpHeaders headers) {
        assertThat(values(headers, "X-Model-Header")).containsExactly("model");
        assertThat(values(headers, "X-Shared")).containsExactly("request");
        assertThat(values(headers, "X-Correlation-Id")).containsExactly("123");
        assertThat(values(headers, "api-key")).containsExactly("test-key");
    }

    private static List<String> values(HttpHeaders headers, String name) {
        return headers.get(HttpHeaderName.fromString(name)).getValuesList();
    }

    /**
     * Records every request that leaves the Azure SDK pipeline and answers with a fixed body.
     */
    private static class CapturingHttpClient implements HttpClient {

        private final String contentType;
        private final String body;
        private final List<HttpRequest> requests = new CopyOnWriteArrayList<>();

        CapturingHttpClient(String contentType, String body) {
            this.contentType = contentType;
            this.body = body;
        }

        HttpRequest singleRequest() {
            assertThat(requests).hasSize(1);
            return requests.get(0);
        }

        @Override
        public Mono<HttpResponse> send(HttpRequest request) {
            requests.add(request);
            return Mono.just(new HttpResponse(request) {

                @Override
                public int getStatusCode() {
                    return 200;
                }

                @Override
                @Deprecated
                public String getHeaderValue(String name) {
                    return getHeaders().getValue(HttpHeaderName.fromString(name));
                }

                @Override
                public HttpHeaders getHeaders() {
                    return new HttpHeaders().set(HttpHeaderName.CONTENT_TYPE, contentType);
                }

                @Override
                public Flux<ByteBuffer> getBody() {
                    return Flux.just(ByteBuffer.wrap(body.getBytes(UTF_8)));
                }

                @Override
                public Mono<byte[]> getBodyAsByteArray() {
                    return Mono.just(body.getBytes(UTF_8));
                }

                @Override
                public Mono<String> getBodyAsString() {
                    return Mono.just(body);
                }

                @Override
                public Mono<String> getBodyAsString(Charset charset) {
                    return Mono.just(body);
                }
            });
        }
    }

    @Test
    void per_request_user_agent_should_be_appended_to_the_default_user_agent() {

        // given
        CapturingHttpClient httpClient = new CapturingHttpClient("application/json", CHAT_COMPLETIONS_JSON);

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .httpClientProvider(providerOf(httpClient))
                .customHeaders(MODEL_HEADERS)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("user-agent", "my-app/1.0", "x-shared", "request"))
                .build();

        // when
        model.chat("Hello");
        model.chat(chatRequest);

        // then
        assertThat(httpClient.requests).hasSize(2);
        List<String> defaultUserAgent = values(httpClient.requests.get(0).getHeaders(), "User-Agent");
        assertThat(defaultUserAgent).containsExactly("langchain4j-azure-openai");
        assertThat(values(httpClient.requests.get(1).getHeaders(), "User-Agent"))
                .containsExactly("langchain4j-azure-openai my-app/1.0");
        assertThat(values(httpClient.requests.get(1).getHeaders(), "X-Shared")).containsExactly("request");
    }

    @Test
    void streaming_per_request_user_agent_should_be_appended_to_the_default_user_agent() throws Exception {

        // given
        CapturingHttpClient httpClient = new CapturingHttpClient("text/event-stream", CHAT_COMPLETIONS_STREAM);

        AzureOpenAiStreamingChatModel model = AzureOpenAiStreamingChatModel.builder()
                .endpoint("https://test.openai.azure.com")
                .apiKey("test-key")
                .deploymentName("gpt-4o-mini")
                .maxRetries(0)
                .httpClientProvider(providerOf(httpClient))
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("User-Agent", "my-app/1.0"))
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });
        futureResponse.get(10, SECONDS);

        // then
        assertThat(values(httpClient.singleRequest().getHeaders(), "User-Agent"))
                .containsExactly("langchain4j-azure-openai my-app/1.0");
    }
}

