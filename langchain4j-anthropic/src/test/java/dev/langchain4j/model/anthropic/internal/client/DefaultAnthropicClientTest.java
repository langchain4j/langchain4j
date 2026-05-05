package dev.langchain4j.model.anthropic.internal.client;

import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCountTokensRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicModelsListResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicStreamingException;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.anthropic.internal.api.MessageTokenCountResponse;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultAnthropicClientTest {
    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_BASE_URL = "https://api.anthropic.com";
    private static final String TEST_VERSION = "2023-06-01";
    private static final String TEST_MODEL_NAME = "claude-3-sonnet-20240229";

    @Nested
    class BuilderValidationTest {

        @Test
        void shouldThrowWhenApiKeyIsMissing() {
            var builder =
                    DefaultAnthropicClient.builder().baseUrl(TEST_BASE_URL).version(TEST_VERSION);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apiKey");
        }

        @Test
        void shouldThrowWhenBaseUrlIsMissing() {
            var builder = DefaultAnthropicClient.builder().apiKey(TEST_API_KEY).version(TEST_VERSION);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseUrl");
        }

        @Test
        void shouldThrowWhenVersionIsMissing() {
            var builder = DefaultAnthropicClient.builder().apiKey(TEST_API_KEY).baseUrl(TEST_BASE_URL);

            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version");
        }
    }

    @Nested
    class CreateMessageTest {

        @Test
        void shouldSendCreateMessageRequest() {
            // Given
            AnthropicCreateMessageResponse expectedResponse = createMessageResponse("Hello, world!");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCreateMessageRequest request = createMessageRequest();

            // When
            AnthropicCreateMessageResponse actualResponse = subject.createMessage(request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectHttpRequest() {
            // Given
            AnthropicCreateMessageResponse expectedResponse = createMessageResponse("Test");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCreateMessageRequest request = createMessageRequest();

            // When
            subject.createMessage(request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/messages");
            assertThat(sentRequest.headers().get("Content-Type")).containsExactly("application/json");
            assertThat(sentRequest.headers().get("x-api-key")).containsExactly(TEST_API_KEY);
            assertThat(sentRequest.headers().get("anthropic-version")).containsExactly(TEST_VERSION);
            assertThat(sentRequest.body()).isEqualTo(Json.toJson(request));
        }

        @Test
        void shouldFlattenCustomParametersWithoutSerializingCustomParametersField() {
            // Given
            AnthropicCreateMessageResponse expectedResponse = createMessageResponse("Test");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCreateMessageRequest request = createMessageRequest().toBuilder()
                    .customParameters(Map.of("output_config", Map.of("effort", "low")))
                    .build();

            // When
            subject.createMessage(request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.body()).contains("\"output_config\"");
            assertThat(sentRequest.body()).contains("\"effort\" : \"low\"");
            assertThat(sentRequest.body()).doesNotContain("\"custom_parameters\"");
        }

        @Test
        void shouldIncludeBetaHeaderWhenSet() {
            // Given
            String beta = "context-management-2025-06-27";
            AnthropicCreateMessageResponse expectedResponse = createMessageResponse("Test");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            DefaultAnthropicClient subject = createClient(mockHttpClient, beta);

            AnthropicCreateMessageRequest request = createMessageRequest();

            // When
            subject.createMessage(request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.headers().get("anthropic-beta")).containsExactly(beta);
        }

        @Test
        void shouldReturnRawResponseWithCreateMessageWithRawResponse() {
            // Given
            AnthropicCreateMessageResponse expectedResponse = createMessageResponse("Test");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCreateMessageRequest request = createMessageRequest();

            // When
            var result = subject.createMessageWithRawResponse(request);

            // Then
            assertThat(result.parsedResponse()).isEqualTo(expectedResponse);
            assertThat(result.rawResponse()).isNotNull();
            assertThat(result.rawResponse().statusCode()).isEqualTo(200);
        }
    }

    @Nested
    class CountTokensTest {

        @Test
        void shouldSendCountTokensRequest() {
            // Given
            var expectedResponse = createMessageTokenCountResponse();
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCountTokensRequest request = AnthropicCountTokensRequest.builder()
                    .model(TEST_MODEL_NAME)
                    .messages(List.of(new AnthropicMessage(USER, List.of(new AnthropicTextContent("Count these")))))
                    .build();

            // When
            var actualResponse = subject.countTokens(request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectCountTokensHttpRequest() {
            // Given
            var expectedResponse = createMessageTokenCountResponse();
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCountTokensRequest request = AnthropicCountTokensRequest.builder()
                    .model(TEST_MODEL_NAME)
                    .messages(List.of(new AnthropicMessage(USER, List.of(new AnthropicTextContent("Test")))))
                    .build();

            // When
            subject.countTokens(request);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/messages/count_tokens");
            assertThat(sentRequest.headers().get("x-api-key")).containsExactly(TEST_API_KEY);
            assertThat(sentRequest.headers().get("anthropic-version")).containsExactly(TEST_VERSION);
        }
    }

    @Nested
    class ListModelsTest {

        @Test
        void shouldSendListModelsRequest() {
            // Given
            var expectedResponse = createEmptyModelsListResponse();
            var httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            var mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            var subject = createClient(mockHttpClient);

            // When
            var actualResponse = subject.listModels();

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }

        @Test
        void shouldSendCorrectListModelsHttpRequest() {
            // Given
            var expectedResponse = createEmptyModelsListResponse();
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            DefaultAnthropicClient subject = createClient(mockHttpClient);

            // When
            subject.listModels();

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.GET);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/models");
            assertThat(sentRequest.headers().get("x-api-key")).containsExactly(TEST_API_KEY);
            assertThat(sentRequest.headers().get("anthropic-version")).containsExactly(TEST_VERSION);
        }
    }

    @Nested
    class StreamingTest {

        @Test
        void shouldStreamCreateMessageResponse() throws Exception {
            // Given
            List<ServerSentEvent> events = List.of(
                    createMessageStartEvent(),
                    createContentBlockStartEvent(),
                    createContentBlockDeltaEvent(),
                    createContentBlockStopEvent(),
                    createMessageDeltaEvent(),
                    createMessageStopEvent());
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCreateMessageRequest request = createMessageRequest();

            CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
            StringBuilder partialResponses = new StringBuilder();

            // When
            subject.createMessage(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    partialResponses.append(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    futureResponse.complete(completeResponse);
                }

                @Override
                public void onError(Throwable error) {
                    futureResponse.completeExceptionally(error);
                }
            });

            ChatResponse response = futureResponse.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(response).isNotNull();
            assertThat(partialResponses.toString()).contains("Hello", " world");
        }

        @Test
        void shouldSendCorrectStreamingHttpRequest() throws Exception {
            // Given
            List<ServerSentEvent> events = List.of(createMessageStartEvent(), createMessageStopEvent());
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCreateMessageRequest request = createMessageRequest();

            CompletableFuture<Void> futureComplete = new CompletableFuture<>();

            // When
            subject.createMessage(request, new StreamingChatResponseHandler() {
                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    futureComplete.complete(null);
                }

                @Override
                public void onError(Throwable error) {
                    futureComplete.completeExceptionally(error);
                }
            });

            futureComplete.get(5, TimeUnit.SECONDS);

            // Then
            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/messages");
            assertThat(sentRequest.headers().get("Content-Type")).containsExactly("application/json");
            assertThat(sentRequest.headers().get("x-api-key")).containsExactly(TEST_API_KEY);
            assertThat(sentRequest.headers().get("anthropic-version")).containsExactly(TEST_VERSION);
        }

        @Test
        void shouldHandleStreamingError() throws Exception {
            // Given
            List<ServerSentEvent> events = List.of(
                    new ServerSentEvent(
                            "error",
                            "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"Rate limit exceeded\"}}"));
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCreateMessageRequest request = createMessageRequest();

            CompletableFuture<Throwable> futureError = new CompletableFuture<>();

            // When
            subject.createMessage(request, new StreamingChatResponseHandler() {
                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    futureError.completeExceptionally(new AssertionError("Expected error"));
                }

                @Override
                public void onError(Throwable error) {
                    futureError.complete(error);
                }
            });

            Throwable error = futureError.get(5, TimeUnit.SECONDS);

            // Then
            assertThat(error).isInstanceOf(AnthropicStreamingException.class);
            assertThat(error.getMessage()).isEqualTo("Rate limit exceeded");
            assertThat(((AnthropicStreamingException) error).type()).isEqualTo("rate_limit_error");
        }

        @Test
        void shouldHandleInterleavedParallelToolCalls() throws Exception {
            // Given: SSE events where two tool calls are interleaved
            // (content_block_start for index=2 arrives before content_block_stop for index=1)
            List<ServerSentEvent> events = List.of(
                    createMessageStartEvent(),
                    // Tool call 1 starts (content block index=1)
                    new ServerSentEvent("content_block_start",
                            "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_1\",\"name\":\"get_weather\"}}"),
                    new ServerSentEvent("content_block_delta",
                            "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"city\\\"\"}}"),
                    // Tool call 2 starts BEFORE tool call 1 stops (content block index=2)
                    new ServerSentEvent("content_block_start",
                            "{\"type\":\"content_block_start\",\"index\":2,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_2\",\"name\":\"get_time\"}}"),
                    new ServerSentEvent("content_block_delta",
                            "{\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"zone\\\"\"}}"),
                    // More deltas for both
                    new ServerSentEvent("content_block_delta",
                            "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\": \\\"Paris\\\"}\"}}"),
                    new ServerSentEvent("content_block_delta",
                            "{\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\": \\\"UTC\\\"}\"}}"),
                    // Tool call 1 stops
                    new ServerSentEvent("content_block_stop",
                            "{\"type\":\"content_block_stop\",\"index\":1}"),
                    // Tool call 2 stops
                    new ServerSentEvent("content_block_stop",
                            "{\"type\":\"content_block_stop\",\"index\":2}"),
                    createMessageDeltaWithToolUse(),
                    createMessageStopEvent());
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(events);

            DefaultAnthropicClient subject = createClient(mockHttpClient);

            AnthropicCreateMessageRequest request = createMessageRequest();

            CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
            List<PartialToolCall> partialToolCalls = new ArrayList<>();
            List<CompleteToolCall> completeToolCalls = new ArrayList<>();

            // When
            subject.createMessage(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialToolCall(PartialToolCall partialToolCall) {
                    partialToolCalls.add(partialToolCall);
                }

                @Override
                public void onCompleteToolCall(CompleteToolCall completeToolCall) {
                    completeToolCalls.add(completeToolCall);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    futureResponse.complete(completeResponse);
                }

                @Override
                public void onError(Throwable error) {
                    futureResponse.completeExceptionally(error);
                }
            });

            ChatResponse response = futureResponse.get(5, TimeUnit.SECONDS);

            // Then: both tool calls should be parsed correctly without mixing
            assertThat(completeToolCalls).hasSize(2);

            CompleteToolCall firstComplete = completeToolCalls.get(0);
            assertThat(firstComplete.toolExecutionRequest().id()).isEqualTo("tool_1");
            assertThat(firstComplete.toolExecutionRequest().name()).isEqualTo("get_weather");
            assertThat(firstComplete.toolExecutionRequest().arguments()).isEqualTo("{\"city\": \"Paris\"}");

            CompleteToolCall secondComplete = completeToolCalls.get(1);
            assertThat(secondComplete.toolExecutionRequest().id()).isEqualTo("tool_2");
            assertThat(secondComplete.toolExecutionRequest().name()).isEqualTo("get_time");
            assertThat(secondComplete.toolExecutionRequest().arguments()).isEqualTo("{\"zone\": \"UTC\"}");

            // Verify the final response also contains both tool execution requests
            List<ToolExecutionRequest> toolRequests =
                    response.aiMessage().toolExecutionRequests();
            assertThat(toolRequests).hasSize(2);
            assertThat(toolRequests.get(0).name()).isEqualTo("get_weather");
            assertThat(toolRequests.get(1).name()).isEqualTo("get_time");
        }
    }

    @Nested
    class TimeoutTest {

        @Test
        void shouldUseCustomTimeout() {
            // Given
            AnthropicCreateMessageResponse expectedResponse = createMessageResponse("Test");
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);

            Duration customTimeout = Duration.ofSeconds(30);
            DefaultAnthropicClient subject = createClient(mockHttpClient, null, customTimeout);

            AnthropicCreateMessageRequest request = createMessageRequest();

            // When
            AnthropicCreateMessageResponse actualResponse = subject.createMessage(request);

            // Then
            assertThat(actualResponse).isEqualTo(expectedResponse);
        }
    }

    // Helper methods

    private static DefaultAnthropicClient createClient(MockHttpClient mockHttpClient) {
        return createClient(mockHttpClient, null, null);
    }

    private static DefaultAnthropicClient createClient(MockHttpClient mockHttpClient, String beta) {
        return createClient(mockHttpClient, beta, null);
    }

    private static DefaultAnthropicClient createClient(MockHttpClient mockHttpClient, String beta, Duration timeout) {
        var builder = DefaultAnthropicClient.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey(TEST_API_KEY)
                .baseUrl(TEST_BASE_URL)
                .version(TEST_VERSION);

        if (beta != null) {
            builder.beta(beta);
        }
        if (timeout != null) {
            builder.timeout(timeout);
        }

        return builder.build();
    }

    private static AnthropicCreateMessageRequest createMessageRequest() {
        return AnthropicCreateMessageRequest.builder()
                .model(TEST_MODEL_NAME)
                .messages(List.of(new AnthropicMessage(USER, List.of(new AnthropicTextContent("Hi")))))
                .maxTokens(1024)
                .build();
    }

    private static AnthropicCreateMessageResponse createMessageResponse(String text) {
        return AnthropicCreateMessageResponse.builder()
                .id("msg_123")
                .type("message")
                .role("assistant")
                .content(List.of(createTextContent(text)))
                .model(TEST_MODEL_NAME)
                .stopReason("end_turn")
                .stopSequence(null)
                .usage(createUsage())
                .build();
    }

    private static ServerSentEvent createMessageStartEvent() {
        String data = String.format(
                "{\"type\":\"message_start\",\"message\":{\"id\":\"%s\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"%s\",\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}}",
                "msg_123", DefaultAnthropicClientTest.TEST_MODEL_NAME);
        return new ServerSentEvent("message_start", data);
    }

    private static ServerSentEvent createContentBlockStartEvent() {
        String data = String.format(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"%s\",\"text\":\"%s\"}}",
                "text", "Hello");
        return new ServerSentEvent("content_block_start", data);
    }

    private static ServerSentEvent createContentBlockDeltaEvent() {
        String data = String.format(
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"%s\"}}",
                " world");
        return new ServerSentEvent("content_block_delta", data);
    }

    private static ServerSentEvent createContentBlockStopEvent() {
        return new ServerSentEvent("content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}");
    }

    private static ServerSentEvent createMessageDeltaEvent() {
        String data = String.format(
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"%s\"},\"usage\":{\"output_tokens\":20}}",
                "end_turn");
        return new ServerSentEvent("message_delta", data);
    }

    private static ServerSentEvent createMessageDeltaWithToolUse() {
        return new ServerSentEvent("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":50}}");
    }

    private static ServerSentEvent createMessageStopEvent() {
        return new ServerSentEvent("message_stop", "{\"type\":\"message_stop\"}");
    }

    private static MessageTokenCountResponse createMessageTokenCountResponse() {
        // inputTokens is private, so we need to use JSON deserialization or add a setter
        // Option 1: Use reflection or JSON
        return Json.fromJson("{\"input_tokens\":42}", MessageTokenCountResponse.class);
    }

    private static AnthropicModelsListResponse createEmptyModelsListResponse() {
        AnthropicModelsListResponse response = new AnthropicModelsListResponse();
        response.data = List.of();
        response.firstId = null;
        response.lastId = null;
        response.hasMore = false;
        return response;
    }

    private static AnthropicContent createTextContent(String text) {
        return AnthropicContent.builder().type("text").text(text).build();
    }

    private static AnthropicUsage createUsage() {
        AnthropicUsage usage = new AnthropicUsage();
        usage.inputTokens = 10;
        usage.outputTokens = 20;
        usage.cacheCreationInputTokens = null;
        usage.cacheReadInputTokens = null;
        return usage;
    }
}
