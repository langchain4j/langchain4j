package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseWebSearchCallCompletedEvent;
import com.openai.models.responses.ResponseWebSearchCallInProgressEvent;
import com.openai.models.responses.ResponseWebSearchCallSearchingEvent;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolSearchTool;
import com.openai.models.responses.WebSearchTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ServerToolExecution;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiOfficialResponsesStreamingChatModelTest {

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
    void should_emit_web_search_server_tool_lifecycle_events() throws Exception {
        RecordingStreamingHandler handler = new RecordingStreamingHandler();
        Object eventHandler = responsesEventHandler(handler);
        Method handleEvent = eventHandler.getClass().getDeclaredMethod("handleEvent", ResponseStreamEvent.class);
        handleEvent.setAccessible(true);
        var inProgressEvent = webSearchInProgress();
        var searchingEvent = webSearchSearching();
        var completedEvent = webSearchCompleted();

        handleEvent.invoke(eventHandler, inProgressEvent);
        handleEvent.invoke(eventHandler, searchingEvent);
        handleEvent.invoke(eventHandler, completedEvent);

        assertThat(handler.beforeServerToolExecutions)
                .containsExactly(serverToolExecution(
                        "response.web_search_call.in_progress", inProgressEvent.asWebSearchCallInProgress()));
        assertThat(handler.serverToolExecutionProgressEvents)
                .containsExactly(serverToolExecution(
                        "response.web_search_call.searching", searchingEvent.asWebSearchCallSearching()));
        assertThat(handler.serverToolExecutedEvents)
                .containsExactly(serverToolExecution(
                        "response.web_search_call.completed", completedEvent.asWebSearchCallCompleted()));
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

    private static Response response(String json) {
        try {
            return ObjectMappers.jsonMapper().readValue(json, Response.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object responsesEventHandler(StreamingChatResponseHandler handler) throws Exception {
        Class<?> handlerClass =
                Class.forName(OpenAiOfficialResponsesStreamingChatModel.class.getName() + "$ResponsesEventHandler");
        var constructor = handlerClass.getDeclaredConstructor(
                StreamingChatResponseHandler.class, AtomicReference.class, String.class, StreamingHandle.class);
        constructor.setAccessible(true);
        return constructor.newInstance(handler, new AtomicReference<>(), "gpt-5.4-mini", null);
    }

    private static ResponseStreamEvent webSearchInProgress() {
        return ResponseStreamEvent.ofWebSearchCallInProgress(ResponseWebSearchCallInProgressEvent.builder()
                .itemId("ws_123")
                .outputIndex(0)
                .sequenceNumber(1)
                .build());
    }

    private static ResponseStreamEvent webSearchSearching() {
        return ResponseStreamEvent.ofWebSearchCallSearching(ResponseWebSearchCallSearchingEvent.builder()
                .itemId("ws_123")
                .outputIndex(0)
                .sequenceNumber(2)
                .build());
    }

    private static ResponseStreamEvent webSearchCompleted() {
        return ResponseStreamEvent.ofWebSearchCallCompleted(ResponseWebSearchCallCompletedEvent.builder()
                .itemId("ws_123")
                .outputIndex(0)
                .sequenceNumber(3)
                .build());
    }

    private static ServerToolExecution serverToolExecution(String type, Object rawEvent) {
        return ServerToolExecution.builder()
                .id("ws_123")
                .type(type)
                .rawEvent(rawEvent)
                .build();
    }

    private static class RecordingStreamingHandler implements StreamingChatResponseHandler {

        private final List<ServerToolExecution> beforeServerToolExecutions = new ArrayList<>();
        private final List<ServerToolExecution> serverToolExecutionProgressEvents = new ArrayList<>();
        private final List<ServerToolExecution> serverToolExecutedEvents = new ArrayList<>();

        @Override
        public void beforeServerToolExecution(ServerToolExecution serverToolExecution) {
            beforeServerToolExecutions.add(serverToolExecution);
        }

        @Override
        public void onServerToolExecutionProgress(ServerToolExecution serverToolExecution) {
            serverToolExecutionProgressEvents.add(serverToolExecution);
        }

        @Override
        public void onServerToolExecuted(ServerToolExecution serverToolExecution) {
            serverToolExecutedEvents.add(serverToolExecution);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {}

        @Override
        public void onError(Throwable error) {
            throw new RuntimeException(error);
        }
    }
}
