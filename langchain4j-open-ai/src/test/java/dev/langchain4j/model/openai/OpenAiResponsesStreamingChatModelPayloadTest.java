package dev.langchain4j.model.openai;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
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

    private static final ToolSpecification DYNAMIC_TOOL = ToolSpecification.builder()
            .name("call_tool")
            .description("Calls a tool discovered via find_tool")
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

    @Test
    void should_send_object_schema_for_tool_without_parameters() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(MODEL_NAME)
                .toolSpecifications(DYNAMIC_TOOL)
                .build();

        try {
            model.chat("Hello", new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        JsonNode tool = OBJECT_MAPPER
                .readTree(mockHttpClient.request().body())
                .get("tools")
                .get(0);
        assertThat(tool.get("parameters").get("type").asText()).isEqualTo("object");
        assertThat(tool.get("parameters").get("properties")).isEmpty();
        assertThat(tool.get("parameters").get("required")).isEmpty();
        assertThat(tool.get("parameters").has("additionalProperties")).isFalse();
        // the Responses API defaults "strict" to true, so it must be sent explicitly
        assertThat(tool.get("strict").asBoolean()).isFalse();
    }

    @Test
    void should_send_strict_object_schema_for_tool_without_parameters_when_strict() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(MODEL_NAME)
                .toolSpecifications(DYNAMIC_TOOL)
                .strictTools(true)
                .build();

        try {
            model.chat("Hello", new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        JsonNode tool = OBJECT_MAPPER
                .readTree(mockHttpClient.request().body())
                .get("tools")
                .get(0);
        assertThat(tool.get("parameters").get("type").asText()).isEqualTo("object");
        assertThat(tool.get("parameters").get("additionalProperties").asBoolean())
                .isFalse();
        assertThat(tool.get("strict").asBoolean()).isTrue();
    }

    @Test
    void should_send_strict_false_explicitly_for_tool_with_parameters() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(MODEL_NAME)
                .toolSpecifications(WEATHER_TOOL)
                .build();

        try {
            model.chat("Hello", new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        JsonNode tool = OBJECT_MAPPER
                .readTree(mockHttpClient.request().body())
                .get("tools")
                .get(0);
        assertThat(tool.get("strict").asBoolean()).isFalse();
    }

    @Test
    void publisher_hook_should_signal_an_unsupported_parameter_instead_of_throwing() {
        MockHttpClient mockHttpClient = new MockHttpClient();
        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(MODEL_NAME)
                .build();
        // the hook is called directly, so the parameters are those of this model, as chat(ChatRequest) passes them
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(OpenAiResponsesChatRequestParameters.builder()
                        .modelName(MODEL_NAME)
                        .topK(3)
                        .build())
                .build();
        List<Object> signals = new CopyOnWriteArrayList<>();

        model.doChat(request).subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                signals.add("onSubscribe");
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                signals.add(event);
            }

            @Override
            public void onError(Throwable error) {
                signals.add(error);
            }

            @Override
            public void onComplete() {
                signals.add("onComplete");
            }
        });

        assertThat(signals).hasSize(2);
        assertThat(signals.get(0)).isEqualTo("onSubscribe");
        assertThat(signals.get(1)).isInstanceOf(UnsupportedFeatureException.class);
        assertThat(mockHttpClient.requests()).isEmpty();
    }
}
