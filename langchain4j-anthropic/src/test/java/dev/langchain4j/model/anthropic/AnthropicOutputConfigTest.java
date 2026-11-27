package dev.langchain4j.model.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicOutputConfigTest {

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("1 + 2?")).build();

    private static final ChatRequest REQUEST_WITH_JSON_SCHEMA = ChatRequest.builder()
            .messages(UserMessage.from("1 + 2?"))
            .responseFormat(ResponseFormat.builder()
                    .type(ResponseFormatType.JSON)
                    .jsonSchema(JsonSchema.builder()
                            .name("sum")
                            .rootElement(JsonObjectSchema.builder()
                                    .addIntegerProperty("sum")
                                    .build())
                            .build())
                    .build())
            .build();

    // fails if the request contains the same key twice
    private static final ObjectMapper STRICT_MAPPER =
            new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void should_merge_custom_output_config_with_response_format() throws Exception {
        chat(
                Map.of(
                        "output_config",
                        Map.of("effort", "low", "format", Map.of("type", "custom")),
                        "inference_geo",
                        "us"),
                REQUEST_WITH_JSON_SCHEMA);

        JsonNode outputConfig = requestBody().get("output_config");
        assertThat(outputConfig.get("effort").asText()).isEqualTo("low");
        assertThat(outputConfig.get("format").get("type").asText()).isEqualTo("json_schema");
        assertThat(requestBody().get("inference_geo").asText()).isEqualTo("us");
    }

    @Test
    void should_not_change_custom_output_config_without_response_format() throws Exception {
        chat(Map.of("output_config", Map.of("effort", "low")), REQUEST);

        assertThat(requestBody().get("output_config")).hasToString("{\"effort\":\"low\"}");
    }

    @Test
    void should_not_modify_custom_parameters() {
        Map<String, Object> outputConfig = new HashMap<>(Map.of("effort", "low"));
        Map<String, Object> customParameters = new HashMap<>(Map.of("output_config", outputConfig));

        chat(customParameters, REQUEST_WITH_JSON_SCHEMA);

        assertThat(customParameters).isEqualTo(Map.of("output_config", Map.of("effort", "low")));
    }

    @Test
    void streaming_should_merge_custom_output_config_with_response_format() throws Exception {
        respondWith("text/event-stream", """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_123","type":"message","role":"assistant","model":"claude-sonnet-4-6","content":[],"usage":{"input_tokens":10,"output_tokens":1}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello!"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":0}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}

                event: message_stop
                data: {"type":"message_stop"}

                """);
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();

        AnthropicStreamingChatModel.builder()
                .baseUrl(baseUrl())
                .apiKey("test-key")
                .modelName("claude-sonnet-4-6")
                .customParameters(Map.of("output_config", Map.of("effort", "low")))
                .build()
                .chat(REQUEST_WITH_JSON_SCHEMA, handler);
        handler.get();

        JsonNode outputConfig = requestBody().get("output_config");
        assertThat(outputConfig.get("effort").asText()).isEqualTo("low");
        assertThat(outputConfig.get("format").get("type").asText()).isEqualTo("json_schema");
    }

    private void chat(Map<String, Object> customParameters, ChatRequest request) {
        respondWith("application/json", """
                {
                  "id": "msg_123",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-sonnet-4-6",
                  "content": [{"type": "text", "text": "Hello!"}],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 10, "output_tokens": 5}
                }
                """);

        AnthropicChatModel.builder()
                .baseUrl(baseUrl())
                .apiKey("test-key")
                .modelName("claude-sonnet-4-6")
                .customParameters(customParameters)
                .build()
                .chat(request);
    }

    private void respondWith(String contentType, String body) {
        wireMockServer.stubFor(post(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", contentType)
                        .withBody(body)));
    }

    private String baseUrl() {
        return "http://localhost:" + wireMockServer.port() + "/v1";
    }

    private JsonNode requestBody() throws Exception {
        List<LoggedRequest> requests = wireMockServer.findAll(postRequestedFor(urlPathEqualTo("/v1/messages")));
        assertThat(requests).hasSize(1);
        return STRICT_MAPPER.readTree(requests.get(0).getBodyAsString());
    }
}
