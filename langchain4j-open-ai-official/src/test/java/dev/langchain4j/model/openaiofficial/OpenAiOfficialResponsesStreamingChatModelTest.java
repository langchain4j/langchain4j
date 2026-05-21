package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolSearchTool;
import com.openai.models.responses.WebSearchTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
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

        OpenAiOfficialResponsesChatRequestParameters defaults =
                OpenAiOfficialResponsesChatRequestParameters.builder()
                        .modelName("gpt-5.4-mini")
                        .serverTools(List.of(webSearch))
                        .build();

        OpenAiOfficialResponsesChatRequestParameters override =
                OpenAiOfficialResponsesChatRequestParameters.builder()
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

        OpenAiOfficialResponsesChatRequestParameters parameters =
                OpenAiOfficialResponsesChatRequestParameters.builder()
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
}
