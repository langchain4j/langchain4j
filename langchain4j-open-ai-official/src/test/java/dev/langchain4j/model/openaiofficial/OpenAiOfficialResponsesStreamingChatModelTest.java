package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams.Input;
import com.openai.models.responses.ResponseIncompleteEvent;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseWebSearchCallInProgressEvent;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolSearchTool;
import com.openai.models.responses.WebSearchTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiOfficialResponsesStreamingChatModelTest {

    private static final OpenAiOfficialResponsesChatRequestParameters PARAMETERS =
            OpenAiOfficialResponsesChatRequestParameters.builder()
                    .modelName("gpt-5.4-mini")
                    .build();

    @Test
    void should_store_server_tools_in_streaming_default_request_parameters() {
        Tool webSearch = webSearchTool();

        OpenAiOfficialResponsesStreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .modelName("gpt-5.4-mini")
                .apiKey("banana")
                .serverTools(webSearch)
                .build();

        OpenAiOfficialResponsesChatRequestParameters parameters =
                (OpenAiOfficialResponsesChatRequestParameters) model.defaultRequestParameters();

        assertThat(parameters.serverTools()).containsExactly(webSearch);
    }

    @Test
    void should_store_server_tools_in_chat_default_request_parameters() {
        Tool toolSearch = toolSearchTool();

        OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .modelName("gpt-5.4-mini")
                .apiKey("banana")
                .serverTools(toolSearch)
                .build();

        OpenAiOfficialResponsesChatRequestParameters parameters =
                (OpenAiOfficialResponsesChatRequestParameters) model.defaultRequestParameters();

        assertThat(parameters.serverTools()).containsExactly(toolSearch);
    }

    @Test
    void should_merge_server_tools_in_request_parameters() {
        Tool webSearch = webSearchTool();
        Tool toolSearch = toolSearchTool();

        OpenAiOfficialResponsesChatRequestParameters defaults = OpenAiOfficialResponsesChatRequestParameters.builder()
                .modelName("gpt-5.4-mini")
                .serverTools(List.of(webSearch))
                .build();

        OpenAiOfficialResponsesChatRequestParameters override = OpenAiOfficialResponsesChatRequestParameters.builder()
                .serverTools(List.of(toolSearch))
                .build();

        OpenAiOfficialResponsesChatRequestParameters merged = defaults.overrideWith(override);

        assertThat(merged.serverTools()).containsExactly(toolSearch);
    }

    @Test
    void should_include_function_and_server_tools_in_request_params() {
        Tool webSearch = webSearchTool();
        ToolSpecification functionTool = ToolSpecification.builder()
                .name("getWeather")
                .description("Returns the current weather for a given city")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        OpenAiOfficialResponsesChatRequestParameters parameters = OpenAiOfficialResponsesChatRequestParameters.builder()
                .modelName("gpt-5.4-mini")
                .toolSpecifications(List.of(functionTool))
                .toolChoice(ToolChoice.REQUIRED)
                .serverTools(List.of(webSearch))
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(parameters)
                .build();

        var requestParams = OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(chatRequest, parameters);

        assertThat(requestParams.tools()).hasValueSatisfying(tools -> {
            assertThat(tools).hasSize(2);
            assertThat(tools.get(0).isFunction()).isTrue();
            assertThat(tools.get(1)).isEqualTo(webSearch);
        });
    }

    @Test
    void should_store_raw_response_in_response_metadata() {
        Response rawResponse = response("""
                {
                  "id": "resp_123",
                  "created_at": 1745310000,
                  "model": "gpt-5.4",
                  "object": "response",
                  "output": [],
                  "parallel_tool_calls": true,
                  "tool_choice": "auto"
                }
                """);

        OpenAiOfficialResponsesChatResponseMetadata metadata = OpenAiOfficialResponsesChatResponseMetadata.builder()
                .id("resp_123")
                .modelName("gpt-5.4")
                .rawResponse(rawResponse)
                .build();

        assertThat(metadata.rawResponse()).isEqualTo(rawResponse);
        assertThat(metadata.toBuilder().build().rawResponse()).isEqualTo(rawResponse);
    }

    @Test
    void should_emit_raw_response_stream_events() {
        RecordingStreamingHandler handler = new RecordingStreamingHandler();
        var eventHandler = new OpenAiOfficialResponsesStreamingChatModel.ResponsesEventHandler(
                handler, new AtomicReference<>(), "gpt-5.4-mini", null);
        var inProgressEvent = webSearchInProgressEvent();

        eventHandler.handleEvent(inProgressEvent);

        assertThat(handler.rawEvents).containsExactly(inProgressEvent);
    }

    private static Tool webSearchTool() {
        return Tool.ofWebSearch(WebSearchTool.builder()
                .type(WebSearchTool.Type.of("web_search"))
                .filters(WebSearchTool.Filters.builder()
                        .allowedDomains(List.of("developers.openai.com"))
                        .build())
                .build());
    }

    private static Tool toolSearchTool() {
        return Tool.ofSearch(ToolSearchTool.builder()
                .type(JsonValue.from("tool_search"))
                .description("Search tools")
                .build());
    }

    private static final String REFUSAL_RESPONSE_JSON = """
            {
              "id": "resp_refusal",
              "created_at": 1745310000,
              "model": "gpt-5.4",
              "object": "response",
              "parallel_tool_calls": true,
              "tool_choice": "auto",
              "output": [
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "status": "completed",
                  "content": [{"type": "refusal", "refusal": "I cannot help with that request."}]
                }
              ]
            }
            """;

    @Test
    void should_extract_refusal_from_response() {
        assertThat(OpenAiOfficialResponsesStreamingChatModel.extractRefusal(response(REFUSAL_RESPONSE_JSON)))
                .isEqualTo("I cannot help with that request.");
    }

    @Test
    void should_return_null_refusal_when_response_contains_only_output_text() {
        Response response = response("""
                {
                  "id": "resp_ok",
                  "created_at": 1745310000,
                  "model": "gpt-5.4",
                  "object": "response",
                  "parallel_tool_calls": true,
                  "tool_choice": "auto",
                  "output": [
                    {
                      "id": "msg_1",
                      "type": "message",
                      "role": "assistant",
                      "status": "completed",
                      "content": [{"type": "output_text", "text": "Paris", "annotations": []}]
                    }
                  ]
                }
                """);

        assertThat(OpenAiOfficialResponsesStreamingChatModel.extractRefusal(response))
                .isNull();
    }

    private static String incompleteResponseJson(String incompleteDetails) {
        return """
                {
                  "id": "resp_incomplete",
                  "created_at": 1745310000,
                  "model": "gpt-5.4",
                  "object": "response",
                  "parallel_tool_calls": true,
                  "tool_choice": "auto",
                  "status": "incomplete",
                  %s
                  "output": [
                    {
                      "id": "msg_1",
                      "type": "message",
                      "role": "assistant",
                      "status": "incomplete",
                      "content": [{"type": "output_text", "text": "Partial", "annotations": []}]
                    }
                  ]
                }
                """.formatted(incompleteDetails);
    }

    private static ChatResponse chatWith(String responseJson) {
        OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .client(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(new CannedJsonHttpClient(responseJson))
                        .build()))
                .modelName("gpt-5.4-mini")
                .build();

        return model.chat(
                ChatRequest.builder().messages(UserMessage.from("Hello")).build());
    }

    @Test
    void should_report_content_filter_when_response_is_incomplete_because_of_the_content_filter() {
        ChatResponse response =
                chatWith(incompleteResponseJson("\"incomplete_details\": {\"reason\": \"content_filter\"},"));

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
    }

    @Test
    void should_report_length_when_response_is_incomplete_because_of_the_token_limit() {
        ChatResponse response =
                chatWith(incompleteResponseJson("\"incomplete_details\": {\"reason\": \"max_output_tokens\"},"));

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_report_length_when_response_is_incomplete_without_details() {
        ChatResponse response = chatWith(incompleteResponseJson(""));

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_report_content_filter_when_stream_ends_incomplete_because_of_the_content_filter() {
        CapturingStreamingHandler handler = new CapturingStreamingHandler();
        var eventHandler = new OpenAiOfficialResponsesStreamingChatModel.ResponsesEventHandler(
                handler, new AtomicReference<>(), "gpt-5.4-mini", new ActiveStreamingHandle());

        eventHandler.handleEvent(ResponseStreamEvent.ofIncomplete(ResponseIncompleteEvent.builder()
                .response(response(incompleteResponseJson("\"incomplete_details\": {\"reason\": \"content_filter\"},")))
                .sequenceNumber(1)
                .build()));

        assertThat(handler.completed.metadata().finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
    }

    @Test
    void should_raise_content_filtered_exception_when_stream_completes_with_refusal() {
        CapturingStreamingHandler handler = new CapturingStreamingHandler();
        var eventHandler = new OpenAiOfficialResponsesStreamingChatModel.ResponsesEventHandler(
                handler, new AtomicReference<>(), "gpt-5.4-mini", new ActiveStreamingHandle());

        eventHandler.handleEvent(ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder()
                .response(response(REFUSAL_RESPONSE_JSON))
                .sequenceNumber(1)
                .build()));

        assertThat(handler.error).isInstanceOf(ContentFilteredException.class);
        assertThat(handler.error).hasMessage("I cannot help with that request.");
        assertThat(handler.completed).isNull();
    }

    @Test
    void should_throw_content_filtered_exception_when_response_contains_refusal() {
        OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .client(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(new CannedJsonHttpClient(REFUSAL_RESPONSE_JSON))
                        .build()))
                .modelName("gpt-5.4-mini")
                .build();

        assertThatThrownBy(() -> model.chat("Hello"))
                .isInstanceOf(ContentFilteredException.class)
                .hasMessage("I cannot help with that request.");
    }

    @Test
    void should_throw_from_chat_when_message_type_is_not_supported() {
        OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .client(new OpenAIClientImpl(ClientOptions.builder()
                        .apiKey("test-key")
                        .httpClient(new CannedJsonHttpClient("{}"))
                        .build()))
                .modelName("gpt-5.4-mini")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(CustomMessage.from(Map.of("role", "developer", "content", "internal note")))
                .build();

        assertThatThrownBy(() -> model.chat(chatRequest))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(
                        "Unsupported message type: dev.langchain4j.data.message.CustomMessage"
                                + ". Only SystemMessage, UserMessage, AiMessage, and ToolExecutionResultMessage are supported.");
    }

    @Test
    void should_map_text_image_and_pdf_user_content_to_responses_input() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(
                        TextContent.from("What is in these?"),
                        ImageContent.from("QUJD", "image/png"),
                        PdfFileContent.from(PdfFile.builder()
                                .base64Data("QUJD")
                                .mimeType("application/pdf")
                                .build())))
                .parameters(PARAMETERS)
                .build();

        var requestParams = OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(chatRequest, PARAMETERS);

        assertThat(requestParams.input().map(Input::asResponse)).hasValueSatisfying(items -> {
            assertThat(items).hasSize(1);
            List<ResponseInputContent> contents =
                    items.get(0).asEasyInputMessage().content().asResponseInputMessageContentList();
            assertThat(contents).hasSize(3);
            assertThat(contents.get(0).isInputText()).isTrue();
            assertThat(contents.get(1).isInputImage()).isTrue();
            assertThat(contents.get(2).isInputFile()).isTrue();
        });
    }

    @Test
    void should_throw_when_message_type_is_not_supported() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(CustomMessage.from(Map.of("role", "developer", "content", "internal note")))
                .parameters(PARAMETERS)
                .build();

        assertThatThrownBy(() -> OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(chatRequest, PARAMETERS))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(
                        "Unsupported message type: dev.langchain4j.data.message.CustomMessage"
                                + ". Only SystemMessage, UserMessage, AiMessage, and ToolExecutionResultMessage are supported.");
    }

    @Test
    void should_throw_when_content_type_is_not_supported() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(AudioContent.from(
                        Audio.builder().base64Data("AAAA").mimeType("audio/mp3").build())))
                .parameters(PARAMETERS)
                .build();

        assertThatThrownBy(() -> OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(chatRequest, PARAMETERS))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("Unsupported content type: dev.langchain4j.data.message.AudioContent"
                        + ". Only TextContent, ImageContent, and PdfFileContent are supported.");
    }

    @Test
    void should_throw_when_video_content_is_not_supported() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(VideoContent.from("QUJD", "video/mp4")))
                .parameters(PARAMETERS)
                .build();

        assertThatThrownBy(() -> OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(chatRequest, PARAMETERS))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("Unsupported content type: dev.langchain4j.data.message.VideoContent"
                        + ". Only TextContent, ImageContent, and PdfFileContent are supported.");
    }

    @Test
    void should_throw_when_user_message_mixes_supported_and_unsupported_content() {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(
                        TextContent.from("Transcribe this"),
                        AudioContent.from(Audio.builder()
                                .base64Data("AAAA")
                                .mimeType("audio/mp3")
                                .build())))
                .parameters(PARAMETERS)
                .build();

        assertThatThrownBy(() -> OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(chatRequest, PARAMETERS))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("Unsupported content type: dev.langchain4j.data.message.AudioContent"
                        + ". Only TextContent, ImageContent, and PdfFileContent are supported.");
    }

    private static class CannedJsonHttpClient implements HttpClient {

        private final String body;

        CannedJsonHttpClient(String body) {
            this.body = body;
        }

        @Override
        public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
            return new CannedJsonHttpResponse(body);
        }

        @Override
        public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
            return CompletableFuture.completedFuture(execute(request, requestOptions));
        }

        @Override
        public void close() {}
    }

    private static class CannedJsonHttpResponse implements HttpResponse {

        private final String body;

        CannedJsonHttpResponse(String body) {
            this.body = body;
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public Headers headers() {
            return Headers.builder().put("Content-Type", "application/json").build();
        }

        @Override
        public InputStream body() {
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {}
    }

    private static class ActiveStreamingHandle implements StreamingHandle {

        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private static class CapturingStreamingHandler implements StreamingChatResponseHandler {

        private Throwable error;
        private ChatResponse completed;

        @Override
        public void onPartialResponse(String partialResponse) {}

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            completed = completeResponse;
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }
    }

    private static Response response(String json) {
        try {
            return ObjectMappers.jsonMapper().readValue(json, Response.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ResponseStreamEvent webSearchInProgressEvent() {
        return ResponseStreamEvent.ofWebSearchCallInProgress(ResponseWebSearchCallInProgressEvent.builder()
                .itemId("ws_123")
                .outputIndex(0)
                .sequenceNumber(1)
                .build());
    }

    private static class RecordingStreamingHandler implements StreamingChatResponseHandler {

        private final List<Object> rawEvents = new ArrayList<>();

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            rawEvents.add(rawEvent);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {}

        @Override
        public void onError(Throwable error) {
            throw new RuntimeException(error);
        }
    }
}
