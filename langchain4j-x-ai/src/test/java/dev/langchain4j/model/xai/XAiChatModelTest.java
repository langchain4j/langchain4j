package dev.langchain4j.model.xai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class XAiChatModelTest {

    private static final String TEXT_RESPONSE = """
            {
              "id": "resp_123",
              "model": "grok-4.7",
              "created_at": 1761200000,
              "status": "completed",
              "output": [
                {
                  "id": "rs_1",
                  "type": "reasoning",
                  "summary": [ { "type": "summary_text", "text": "Thinking..." } ],
                  "encrypted_content": "ENCRYPTED"
                },
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "status": "completed",
                  "content": [ { "type": "output_text", "text": "Hello!", "annotations": [] } ]
                }
              ],
              "usage": { "input_tokens": 10, "output_tokens": 5, "total_tokens": 15 }
            }
            """;

    private static final String TOOL_CALL_RESPONSE = """
            {
              "id": "resp_456",
              "model": "grok-4.7",
              "status": "completed",
              "output": [
                {
                  "type": "function_call",
                  "id": "fc_1",
                  "call_id": "call_1",
                  "name": "getWeather",
                  "arguments": "{\\"city\\":\\"Paris\\"}"
                }
              ]
            }
            """;

    private static final String INCOMPLETE_RESPONSE = """
            {
              "id": "resp_789",
              "model": "grok-4.7",
              "status": "incomplete",
              "incomplete_details": { "reason": "max_output_tokens" },
              "output": [
                {
                  "type": "message",
                  "role": "assistant",
                  "content": [ { "type": "output_text", "text": "Truncated" } ]
                }
              ]
            }
            """;

    @Test
    void should_send_request_and_parse_text_response() {
        MockHttpClient httpClient = new MockHttpClient(response(TEXT_RESPONSE));

        XAiChatModel model = XAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName(XAiChatModelName.GROK_4_7)
                .temperature(0.5)
                .maxOutputTokens(100)
                .reasoningEffort("high")
                .store(false)
                .build();

        ChatResponse chatResponse = model.chat(
                ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        // request
        var request = httpClient.request();
        assertThat(request.url()).isEqualTo("https://api.x.ai/v1/responses");
        assertThat(request.headers().get("Authorization")).containsExactly("Bearer test-key");

        Map<String, Object> body = Json.fromJson(request.body(), Map.class);
        assertThat(body)
                .containsEntry("model", "grok-4.7")
                .containsEntry("temperature", 0.5)
                .containsEntry("max_output_tokens", 100)
                .containsEntry("store", false)
                .containsEntry("reasoning", Map.of("effort", "high"))
                .containsEntry(
                        "input",
                        List.of(Map.of(
                                "type", "message",
                                "role", "user",
                                "content", List.of(Map.of("type", "input_text", "text", "Hi")))));

        // response
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualTo("Hello!");
        assertThat(aiMessage.thinking()).isEqualTo("Thinking...");
        assertThat(aiMessage.attribute(XAiClient.ENCRYPTED_REASONING_KEY, String.class))
                .isEqualTo("ENCRYPTED");

        assertThat(chatResponse.metadata()).isInstanceOf(XAiChatResponseMetadata.class);
        XAiChatResponseMetadata metadata = (XAiChatResponseMetadata) chatResponse.metadata();
        assertThat(metadata.id()).isEqualTo("resp_123");
        assertThat(metadata.modelName()).isEqualTo("grok-4.7");
        assertThat(metadata.createdAt()).isEqualTo(1761200000L);
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(metadata.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(metadata.tokenUsage().outputTokenCount()).isEqualTo(5);
        assertThat(metadata.tokenUsage().totalTokenCount()).isEqualTo(15);
    }

    @Test
    void should_send_tools_and_parse_tool_calls() {
        MockHttpClient httpClient = new MockHttpClient(response(TOOL_CALL_RESPONSE));

        XAiChatModel model = XAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .modelName("grok-4.7")
                .build();

        ToolSpecification weather = ToolSpecification.builder()
                .name("getWeather")
                .description("Returns the weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        ChatResponse chatResponse = model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Weather in Paris?"))
                .toolSpecifications(weather)
                .build());

        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0))
                .containsEntry("type", "function")
                .containsEntry("name", "getWeather")
                .containsEntry("description", "Returns the weather");

        assertThat(chatResponse.aiMessage().toolExecutionRequests())
                .containsExactly(ToolExecutionRequest.builder()
                        .id("call_1")
                        .name("getWeather")
                        .arguments("{\"city\":\"Paris\"}")
                        .build());
        assertThat(chatResponse.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }

    @Test
    void should_map_conversation_to_input_items() {
        MockHttpClient httpClient = new MockHttpClient(response(TEXT_RESPONSE));

        XAiChatModel model = XAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .modelName("grok-4.7")
                .build();

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getWeather")
                .arguments("{\"city\":\"Paris\"}")
                .build();
        AiMessage previousAiMessage = AiMessage.builder()
                .thinking("Thinking...")
                .toolExecutionRequests(List.of(toolExecutionRequest))
                .attributes(Map.of(XAiClient.ENCRYPTED_REASONING_KEY, "ENCRYPTED"))
                .build();

        model.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from("Be brief"),
                        UserMessage.from("Weather in Paris?"),
                        previousAiMessage,
                        ToolExecutionResultMessage.from(toolExecutionRequest, "Sunny"))
                .build());

        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        List<Map<String, Object>> input = (List<Map<String, Object>>) body.get("input");
        assertThat(input).hasSize(5);
        assertThat(input.get(0)).containsEntry("role", "system");
        assertThat(input.get(1)).containsEntry("role", "user");
        assertThat(input.get(2))
                .containsEntry("type", "reasoning")
                .containsEntry("encrypted_content", "ENCRYPTED")
                .containsEntry("summary", List.of(Map.of("type", "summary_text", "text", "Thinking...")));
        assertThat(input.get(3))
                .containsEntry("type", "function_call")
                .containsEntry("call_id", "call_1")
                .containsEntry("name", "getWeather")
                .containsEntry("arguments", "{\"city\":\"Paris\"}");
        assertThat(input.get(4))
                .containsEntry("type", "function_call_output")
                .containsEntry("call_id", "call_1")
                .containsEntry("output", "Sunny");
    }

    @Test
    void should_send_json_schema_response_format() {
        MockHttpClient httpClient = new MockHttpClient(response(TEXT_RESPONSE));

        XAiChatModel model = XAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .modelName("grok-4.7")
                .build();

        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("John is 42"))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("name")
                                        .addIntegerProperty("age")
                                        .build())
                                .build())
                        .build())
                .build());

        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        Map<String, Object> format = (Map<String, Object>) ((Map<String, Object>) body.get("text")).get("format");
        assertThat(format)
                .containsEntry("type", "json_schema")
                .containsEntry("name", "Person")
                .containsEntry("strict", true)
                .containsKey("schema");
    }

    @Test
    void should_map_incomplete_status_to_length() {
        MockHttpClient httpClient = new MockHttpClient(response(INCOMPLETE_RESPONSE));

        XAiChatModel model = XAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .modelName("grok-4.7")
                .build();

        ChatResponse chatResponse = model.chat(UserMessage.from("Tell me a long story"));

        assertThat(chatResponse.aiMessage().text()).isEqualTo("Truncated");
        assertThat(chatResponse.finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_override_default_parameters_per_request() {
        MockHttpClient httpClient = new MockHttpClient(response(TEXT_RESPONSE));

        XAiChatModel model = XAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .modelName("grok-4.7")
                .reasoningEffort("low")
                .build();

        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(XAiChatRequestParameters.builder()
                        .modelName(XAiChatModelName.GROK_4_20)
                        .previousResponseId("resp_0")
                        .build())
                .build());

        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat(body)
                .containsEntry("model", "grok-4.20")
                .containsEntry("previous_response_id", "resp_0")
                .containsEntry("reasoning", Map.of("effort", "low"));
    }

    @Test
    void should_reject_unsupported_parameters() {
        XAiChatModel model = XAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(new MockHttpClient(response(TEXT_RESPONSE))))
                .modelName("grok-4.7")
                .build();

        assertThatThrownBy(() -> model.chat(ChatRequest.builder()
                        .messages(UserMessage.from("Hi"))
                        .frequencyPenalty(0.5)
                        .build()))
                .isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void should_report_provider() {
        XAiChatModel model = XAiChatModel.builder().modelName("grok-4.7").build();

        assertThat(model.provider()).isEqualTo(ModelProvider.X_AI);
    }

    @Test
    void language_model_should_generate_text() {
        MockHttpClient httpClient = new MockHttpClient(response(TEXT_RESPONSE));

        XAiLanguageModel model = XAiLanguageModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .modelName(XAiChatModelName.GROK_4_7)
                .temperature(0.0)
                .build();

        Response<String> response = model.generate("Say hello");

        assertThat(response.content()).isEqualTo("Hello!");
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(15);
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);

        Map<String, Object> body = Json.fromJson(httpClient.request().body(), Map.class);
        assertThat(body).containsEntry("model", "grok-4.7").containsEntry("temperature", 0.0);
    }

    private static SuccessfulHttpResponse response(String body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(body)
                .build();
    }
}
