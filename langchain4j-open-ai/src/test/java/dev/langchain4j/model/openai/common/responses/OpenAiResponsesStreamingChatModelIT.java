package dev.langchain4j.model.openai.common.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private static final String GPT_5_4_MINI = "gpt-5.4-mini";
    private static final int MAX_OUTPUT_TOKENS_MIN_VALUE = 16;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected List<StreamingChatModel> models() {
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_5_4_MINI)
                .logRequests(false) // images are huge in logs
                .logResponses(true)
                .build();

        return List.of(model);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), GPT_5_4_MINI))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20";
    }

    @Override
    protected int maxOutputTokens() {
        return MAX_OUTPUT_TOKENS_MIN_VALUE;
    }

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters.overrideWith(ChatRequestParameters.builder()
                .maxOutputTokens(MAX_OUTPUT_TOKENS_MIN_VALUE)
                .build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiResponsesChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiResponsesChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiTokenUsage.class;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_5_4_MINI)
                .listeners(listener)
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 0
                        && toolCall.id().equals(id)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 0
                            && request.id().equals(id)
                            && request.name().equals("getWeather")
                            && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
                }
        ));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 0
                        && toolCall.id().equals(id1)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 0
                            && request.id().equals(id1)
                            && request.name().equals("getWeather")
                            && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
                }
        ));

        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 1
                        && toolCall.id().equals(id2)
                        && toolCall.name().equals("getTime")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 1
                            && request.id().equals(id2)
                            && request.name().equals("getTime")
                            && request.arguments().replace(" ", "").equals("{\"country\":\"France\"}");
                }
        ));
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false;
    }

    @Test
    void should_return_model_specific_response_metadata() {
        String serviceTier = "default";

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4_MINI)
                .serviceTier(serviceTier)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is 2+2?", handler);

        ChatResponseMetadata metadata = handler.get().metadata();
        assertThat(metadata).isInstanceOf(OpenAiResponsesChatResponseMetadata.class);

        OpenAiResponsesChatResponseMetadata openAiMetadata = (OpenAiResponsesChatResponseMetadata) metadata;
        assertThat(openAiMetadata.id()).isNotBlank();
        assertThat(openAiMetadata.modelName()).startsWith(GPT_5_4_MINI);
        assertThat(openAiMetadata.finishReason()).isEqualTo(STOP);
        assertThat(openAiMetadata.createdAt()).isGreaterThan(0);
        assertThat(openAiMetadata.completedAt()).isGreaterThan(0);
        assertThat(openAiMetadata.completedAt()).isGreaterThanOrEqualTo(openAiMetadata.createdAt());
        assertThat(openAiMetadata.serviceTier()).isEqualTo(serviceTier);
        assertThat(openAiMetadata.rawHttpResponse()).isNotNull();
        assertThat(openAiMetadata.rawServerSentEvents()).isNotEmpty();

        OpenAiTokenUsage tokenUsage = openAiMetadata.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }

    @Test
    void should_work_with_o_models() {
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("o4-mini")
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        ChatResponse response = handler.get();
        assertThat(response.aiMessage().text()).contains("Berlin");
        assertThat(response.metadata()).isInstanceOf(OpenAiResponsesChatResponseMetadata.class);
        OpenAiResponsesChatResponseMetadata metadata = (OpenAiResponsesChatResponseMetadata) response.metadata();
        assertThat(metadata.id()).isNotBlank();
        assertThat(metadata.modelName()).isNotBlank();
        assertThat(metadata.finishReason()).isNotNull();
    }

    @Test
    void should_support_reasoning_effort() {
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("o4-mini")
                .reasoningEffort("medium")
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of France?", handler);

        ChatResponse response = handler.get();
        assertThat(response.aiMessage().text()).contains("Paris");
        assertThat(response.metadata()).isInstanceOf(OpenAiResponsesChatResponseMetadata.class);
        OpenAiResponsesChatResponseMetadata metadata = (OpenAiResponsesChatResponseMetadata) response.metadata();
        assertThat(metadata.id()).isNotBlank();
        assertThat(metadata.modelName()).isNotBlank();
        assertThat(metadata.finishReason()).isNotNull();
    }

    @Test
    void should_propagate_all_Responses_API_specific_parameters() {
        MockHttpClient mockHttpClient = new MockHttpClient();

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4_MINI)
                .temperature(0.7)
                .topP(0.9)
                .maxOutputTokens(100)
                .maxToolCalls(2)
                .parallelToolCalls(false)
                .topLogprobs(5)
                .truncation("auto")
                .include(List.of("message.output_text.logprobs", "reasoning.encrypted_content"))
                .serviceTier("default")
                .reasoningEffort("low")
                .promptCacheRetention("24h")
                .textVerbosity("medium")
                .streamIncludeObfuscation(true)
                .build();

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("Hello")).build();

        try {
            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            model.chat(chatRequest, handler);
        } catch (Exception e) {
        }

        assertThat(mockHttpClient.request().body())
                .containsIgnoringWhitespaces("\"temperature\": 0.7")
                .containsIgnoringWhitespaces("\"top_p\": 0.9")
                .containsIgnoringWhitespaces("\"max_output_tokens\": 100")
                .containsIgnoringWhitespaces("\"max_tool_calls\": 2")
                .containsIgnoringWhitespaces("\"parallel_tool_calls\": false")
                .containsIgnoringWhitespaces("\"top_logprobs\": 5")
                .containsIgnoringWhitespaces("\"truncation\": \"auto\"")
                .containsIgnoringWhitespaces(
                        "\"include\": [\"message.output_text.logprobs\", \"reasoning.encrypted_content\"]")
                .containsIgnoringWhitespaces("\"service_tier\": \"default\"")
                .containsIgnoringWhitespaces("\"reasoning\": {\"effort\": \"low\"}")
                .containsIgnoringWhitespaces("\"prompt_cache_retention\": \"24h\"")
                .containsIgnoringWhitespaces("\"text\": {\"verbosity\": \"medium\"}")
                .containsIgnoringWhitespaces("\"stream_options\": {\"include_obfuscation\": true}");
    }

    @Test
    void should_send_previous_response_id_from_request_parameters() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-key")
                .modelName(GPT_5_4_MINI)
                .previousResponseId("builder-response-id")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(OpenAiResponsesChatRequestParameters.builder()
                        .previousResponseId("request-response-id")
                        .build())
                .build();

        try {
            model.chat(chatRequest, new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        JsonNode payload = OBJECT_MAPPER.readTree(mockHttpClient.request().body());
        assertThat(payload.get("previous_response_id").asText()).isEqualTo("request-response-id");
    }

    @Test
    void should_send_previous_response_id_from_default_request_parameters() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .defaultRequestParameters(OpenAiResponsesChatRequestParameters.builder()
                        .modelName(GPT_5_4_MINI)
                        .previousResponseId("default-response-id")
                        .build())
                .build();

        try {
            model.chat("Hello", new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        JsonNode payload = OBJECT_MAPPER.readTree(mockHttpClient.request().body());
        assertThat(payload.get("previous_response_id").asText()).isEqualTo("default-response-id");
    }

    @Test
    void should_emit_partial_thinking_for_reasoning_deltas() {
        MockHttpClient mockHttpClient = new MockHttpClient(List.of(
                new ServerSentEvent(null, "{\"type\":\"response.reasoning_text.delta\",\"delta\":\"let me\"}"),
                new ServerSentEvent(null, "{\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\" think\"}"),
                new ServerSentEvent(
                        null,
                        "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_123\",\"model\":\"gpt-4.1-nano\",\"status\":\"completed\",\"output\":[],\"usage\":{\"input_tokens\":1,\"output_tokens\":0,\"total_tokens\":1}}}"),
                new ServerSentEvent(null, "[DONE]")));

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_5_4_MINI)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        handler.get();
        assertThat(handler.getThinking()).isEqualTo("let me think");
    }

    @Test
    void should_complete_response_when_completed_event_has_no_text_or_tool_calls() {
        MockHttpClient mockHttpClient = new MockHttpClient(List.of(
                new ServerSentEvent(
                        null,
                        "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_123\",\"model\":\"gpt-4.1-nano\",\"status\":\"completed\",\"output\":[],\"usage\":{\"input_tokens\":1,\"output_tokens\":0,\"total_tokens\":1}}}"),
                new ServerSentEvent(null, "[DONE]")));

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_5_4_MINI)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        ChatResponse response = handler.get();
        assertThat(response.aiMessage().text()).isEmpty();
        assertThat(response.metadata().finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_surface_response_failed_message_from_error_node() {
        MockHttpClient mockHttpClient = new MockHttpClient(List.of(
                new ServerSentEvent(
                        null,
                        "{\"type\":\"response.failed\",\"response\":{\"error\":{\"message\":\"tokens exceeded\"}}}"),
                new ServerSentEvent(null, "[DONE]")));

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_5_4_MINI)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        assertThatThrownBy(handler::get).rootCause().hasMessage("Response failed: tokens exceeded");
    }

    @Test
    void should_surface_response_error_message_from_error_node() {
        MockHttpClient mockHttpClient = new MockHttpClient(List.of(
                new ServerSentEvent(null, "{\"type\":\"response.error\",\"error\":{\"message\":\"request rejected\"}}"),
                new ServerSentEvent(null, "[DONE]")));

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_5_4_MINI)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        assertThatThrownBy(handler::get).rootCause().hasMessage("Response failed: request rejected");
    }

    @Test
    void should_map_incomplete_status_to_length_for_completed_callback() {
        MockHttpClient mockHttpClient = new MockHttpClient(List.of(
                new ServerSentEvent(
                        null,
                        "{\"type\":\"response.incomplete\",\"response\":{\"id\":\"resp_123\",\"model\":\"gpt-4.1-nano\",\"status\":\"incomplete\",\"output\":[],\"usage\":{\"input_tokens\":1,\"output_tokens\":0,\"total_tokens\":1}}}"),
                new ServerSentEvent(null, "[DONE]")));

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_5_4_MINI)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Hello", handler);

        ChatResponse response = handler.get();
        assertThat(response.aiMessage().text()).isEmpty();
        assertThat(response.metadata().finishReason()).isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
    }
}
