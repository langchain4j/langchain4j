package dev.langchain4j.model.openai;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiResponsesStreamingChatModelPayloadTest {

    private static final String MODEL_NAME = "gpt-5.4-mini";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ToolSpecification WEATHER_TOOL = ToolSpecification.builder()
            .name("getWeather")
            .description("Returns the current weather for a given city")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city")
                    .required("city")
                    .build())
            .build();

    @Test
    void should_send_only_server_tools() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();
        Map<String, Object> webSearchTool = new LinkedHashMap<>();
        webSearchTool.put("type", "web_search");
        webSearchTool.put("user_location", Map.of("type", "approximate", "country", "US"));

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(MODEL_NAME)
                .serverTools(List.of(webSearchTool))
                .build();

        try {
            model.chat("Hello", new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        JsonNode payload = OBJECT_MAPPER.readTree(mockHttpClient.request().body());
        assertThat(payload.get("tools")).hasSize(1);
        assertThat(payload.get("tools").get(0).get("type").asText()).isEqualTo("web_search");
        assertThat(payload.get("tools")
                        .get(0)
                        .get("user_location")
                        .get("country")
                        .asText())
                .isEqualTo("US");
    }

    @Test
    void should_send_function_and_server_tools_together() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();
        Map<String, Object> webSearchTool = new LinkedHashMap<>();
        webSearchTool.put("type", "web_search");

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(MODEL_NAME)
                .toolSpecifications(WEATHER_TOOL)
                .serverTools(List.of(webSearchTool))
                .toolChoice(REQUIRED)
                .build();

        try {
            model.chat("Hello", new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        JsonNode payload = OBJECT_MAPPER.readTree(mockHttpClient.request().body());
        assertThat(payload.get("tools")).hasSize(2);
        assertThat(payload.get("tools").get(0).get("type").asText()).isEqualTo("function");
        assertThat(payload.get("tools").get(0).get("name").asText()).isEqualTo("getWeather");
        assertThat(payload.get("tools").get(1).get("type").asText()).isEqualTo("web_search");
        assertThat(payload.get("tool_choice").asText()).isEqualTo("required");
    }

    @Test
    void should_override_default_server_tools_with_request_server_tools() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();
        Map<String, Object> defaultTool = new LinkedHashMap<>();
        defaultTool.put("type", "web_search");
        Map<String, Object> requestTool = new LinkedHashMap<>();
        requestTool.put("type", "file_search");
        requestTool.put("vector_store_ids", List.of("vs_1"));

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .defaultRequestParameters(OpenAiResponsesChatRequestParameters.builder()
                        .modelName(MODEL_NAME)
                        .serverTools(List.of(defaultTool))
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from("Hello"))
                .parameters(OpenAiResponsesChatRequestParameters.builder()
                        .serverTools(List.of(requestTool))
                        .build())
                .build();

        try {
            model.chat(chatRequest, new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        JsonNode payload = OBJECT_MAPPER.readTree(mockHttpClient.request().body());
        assertThat(payload.get("tools")).hasSize(1);
        assertThat(payload.get("tools").get(0).get("type").asText()).isEqualTo("file_search");
        assertThat(payload.get("tools").get(0).get("vector_store_ids").get(0).asText())
                .isEqualTo("vs_1");
    }
}
