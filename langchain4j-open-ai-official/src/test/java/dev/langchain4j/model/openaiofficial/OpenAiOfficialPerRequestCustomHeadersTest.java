package dev.langchain4j.model.openaiofficial;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.batch.BatchRequest;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiOfficialPerRequestCustomHeadersTest {

    private static final String CHAT_COMPLETION_JSON =
            """
            {"id":"chatcmpl-1","object":"chat.completion","created":1721596428,"model":"gpt-4o-mini",\
            "choices":[{"index":0,"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}]}""";

    private static final String CHAT_COMPLETION_STREAM =
            """
            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1721596428,"model":"gpt-4o-mini",\
            "choices":[{"index":0,"delta":{"role":"assistant","content":"Hello!"},"finish_reason":null}]}

            data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1721596428,"model":"gpt-4o-mini",\
            "choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """;

    private static final String RESPONSE_JSON =
            """
            {"id":"resp_1","object":"response","created_at":1721596428,"model":"gpt-4o-mini","status":"completed",\
            "output":[{"type":"message","id":"msg_1","role":"assistant","status":"completed",\
            "content":[{"type":"output_text","text":"Hi","annotations":[]}]}],\
            "parallel_tool_calls":true,"tool_choice":"auto","tools":[],"error":null,"incomplete_details":null,\
            "instructions":null,"metadata":{},"temperature":1.0,"top_p":1.0}""";

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

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void chat_model_should_send_per_request_headers() {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/chat/completions"))
                .willReturn(WireMock.okJson(CHAT_COMPLETION_JSON)));

        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        model.chat(CHAT_REQUEST);

        // then
        assertPerRequestHeadersSent(singleRequest().getHeaders());
    }

    @Test
    void per_request_header_should_replace_authorization_header() {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/chat/completions"))
                .willReturn(WireMock.okJson(CHAT_COMPLETION_JSON)));

        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("model-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("authorization", "Bearer request-key"))
                .build();

        // when
        model.chat(chatRequest);

        // then
        assertThat(singleRequest().getHeaders().getHeader("Authorization").values())
                .containsExactly("Bearer request-key");
    }

    @Test
    void chat_model_should_not_send_per_request_headers_of_previous_request() {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/chat/completions"))
                .willReturn(WireMock.okJson(CHAT_COMPLETION_JSON)));

        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .build();

        // when
        model.chat(CHAT_REQUEST);
        model.chat("Hello again");

        // then
        List<LoggedRequest> requests = wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl()));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).getHeaders().getHeader("X-Correlation-Id").isPresent())
                .isFalse();
    }

    @Test
    void streaming_chat_model_should_send_per_request_headers() throws Exception {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/chat/completions"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(CHAT_COMPLETION_STREAM)));

        OpenAiOfficialStreamingChatModel model = OpenAiOfficialStreamingChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .customHeaders(MODEL_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(CHAT_REQUEST, handler(futureResponse));

        // then
        assertThat(futureResponse.get(10, SECONDS).aiMessage().text()).isEqualTo("Hello!");
        assertPerRequestHeadersSent(singleRequest().getHeaders());
    }

    @Test
    void responses_chat_model_should_send_per_request_headers() {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/responses"))
                .willReturn(WireMock.okJson(RESPONSE_JSON)));

        OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .customHeaders(MODEL_HEADERS)
                .build();

        // when
        ChatResponse chatResponse = model.chat(CHAT_REQUEST);

        // then
        assertThat(chatResponse.aiMessage().text()).isEqualTo("Hi");
        assertPerRequestHeadersSent(singleRequest().getHeaders());
    }

    @Test
    void responses_streaming_chat_model_should_send_per_request_headers() throws Exception {

        // given
        String stream = "event: response.completed\ndata: {\"type\":\"response.completed\",\"sequence_number\":1,"
                + "\"response\":" + RESPONSE_JSON + "}\n\n";
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/responses"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(stream)));

        OpenAiOfficialResponsesStreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .customHeaders(MODEL_HEADERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(CHAT_REQUEST, handler(futureResponse));

        // then
        futureResponse.get(10, SECONDS);
        assertPerRequestHeadersSent(singleRequest().getHeaders());
    }

    @Test
    void batch_chat_model_should_reject_per_request_headers() {

        // given
        OpenAiOfficialBatchChatModel model = OpenAiOfficialBatchChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();

        // when-then
        assertThatThrownBy(() -> model.submit(new BatchRequest<>(List.of(CHAT_REQUEST))))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters");
        assertThat(wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl())))
                .isEmpty();
    }

    @Test
    void chat_model_should_send_headers_from_default_request_parameters() {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/chat/completions"))
                .willReturn(WireMock.okJson(CHAT_COMPLETION_JSON)));

        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .defaultRequestParameters(DEFAULT_PARAMETERS)
                .build();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER);

        // then
        assertDefaultHeadersMergedWithRequestHeaders(singleRequest().getHeaders());
    }

    @Test
    void streaming_chat_model_should_send_headers_from_default_request_parameters() throws Exception {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/chat/completions"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(CHAT_COMPLETION_STREAM)));

        OpenAiOfficialStreamingChatModel model = OpenAiOfficialStreamingChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .defaultRequestParameters(DEFAULT_PARAMETERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER, handler(futureResponse));

        // then
        futureResponse.get(10, SECONDS);
        assertDefaultHeadersMergedWithRequestHeaders(singleRequest().getHeaders());
    }

    @Test
    void responses_chat_model_should_send_headers_from_default_request_parameters() {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/responses"))
                .willReturn(WireMock.okJson(RESPONSE_JSON)));

        OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .defaultRequestParameters(DEFAULT_PARAMETERS)
                .build();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER);

        // then
        assertDefaultHeadersMergedWithRequestHeaders(singleRequest().getHeaders());
    }

    @Test
    void responses_streaming_chat_model_should_send_headers_from_default_request_parameters() throws Exception {

        // given
        String stream = "event: response.completed\ndata: {\"type\":\"response.completed\",\"sequence_number\":1,"
                + "\"response\":" + RESPONSE_JSON + "}\n\n";
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/responses"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(stream)));

        OpenAiOfficialResponsesStreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .defaultRequestParameters(DEFAULT_PARAMETERS)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat(REQUEST_WITH_SHARED_HEADER, handler(futureResponse));

        // then
        futureResponse.get(10, SECONDS);
        assertDefaultHeadersMergedWithRequestHeaders(singleRequest().getHeaders());
    }

    @Test
    void batch_chat_model_should_reject_headers_in_default_request_parameters_at_build_time() {

        assertThatThrownBy(() -> OpenAiOfficialBatchChatModel.builder()
                        .baseUrl(wireMockServer.baseUrl())
                        .apiKey("test-key")
                        .modelName("gpt-4o-mini")
                        .defaultRequestParameters(DEFAULT_PARAMETERS)
                        .build())
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters")
                .hasMessageContaining("batch");
        assertThat(wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl())))
                .isEmpty();
    }

    private static void assertDefaultHeadersMergedWithRequestHeaders(HttpHeaders headers) {
        assertThat(headers.getHeader("X-Default").values()).containsExactly("d");
        assertThat(headers.getHeader("X-Shared").values()).containsExactly("request");
    }

    private LoggedRequest singleRequest() {
        List<LoggedRequest> requests = wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl()));
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    private static void assertPerRequestHeadersSent(HttpHeaders headers) {
        assertThat(headers.getHeader("X-Model-Header").values()).containsExactly("model");
        assertThat(headers.getHeader("X-Shared").values()).containsExactly("request");
        assertThat(headers.getHeader("X-Correlation-Id").values()).containsExactly("123");
        assertThat(headers.getHeader("Authorization").values()).containsExactly("Bearer test-key");
    }

    private static StreamingChatResponseHandler handler(CompletableFuture<ChatResponse> futureResponse) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        };
    }

    @Test
    void per_request_user_agent_should_be_appended_to_the_default_user_agent() {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/chat/completions"))
                .willReturn(WireMock.okJson(CHAT_COMPLETION_JSON)));

        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("user-agent", "my-app/1.0", "X-Shared", "request"))
                .build();

        // when
        model.chat("Hello");
        model.chat(chatRequest);

        // then
        List<LoggedRequest> requests = wireMockServer.findAll(
                WireMock.postRequestedFor(WireMock.urlPathMatching(".*/chat/completions")));
        assertThat(requests).hasSize(2);
        String defaultUserAgent = requests.get(0).getHeader("User-Agent");
        assertThat(defaultUserAgent).isEqualTo("langchain4j-openai-official");
        assertThat(requests.get(1).getHeaders().getHeader("User-Agent").values())
                .containsExactly(defaultUserAgent + " my-app/1.0");
        assertThat(requests.get(1).getHeader("X-Shared")).isEqualTo("request");
    }

    @Test
    void responses_streaming_chat_model_should_append_per_request_user_agent() throws Exception {

        // given
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(".*/responses"))
                .willReturn(WireMock.aResponse().withStatus(500)));

        OpenAiOfficialResponsesStreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(wireMockServer.baseUrl())
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .maxRetries(0)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .customHeaders(Map.of("User-Agent", "my-app/1.0"))
                .build();

        CompletableFuture<Throwable> done = new CompletableFuture<>();

        // when
        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                done.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                done.complete(error);
            }
        });
        done.get(10, SECONDS);

        // then
        LoggedRequest request = wireMockServer
                .findAll(WireMock.postRequestedFor(WireMock.urlPathMatching(".*/responses")))
                .get(0);
        assertThat(request.getHeaders().getHeader("User-Agent").values())
                .containsExactly("langchain4j-openai-official my-app/1.0");
    }
}

