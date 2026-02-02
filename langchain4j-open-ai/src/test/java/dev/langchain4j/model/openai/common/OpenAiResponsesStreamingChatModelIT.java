package dev.langchain4j.model.openai.common;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_1_NANO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatModel> models() {
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_1_NANO.toString())
                .logRequests(true)
                .logResponses(true)
                .build();

        return List.of(model);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        OpenAiResponsesStreamingChatModel.Builder modelBuilder = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"));

        if (parameters.modelName() != null) {
            modelBuilder.modelName(parameters.modelName());
        } else {
            modelBuilder.modelName(GPT_4_1_NANO.toString());
        }

        return modelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiChatRequestParameters.builder()
                .maxOutputTokens(Math.max(maxOutputTokens, 16))
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiTokenUsage.class;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false;
    }

    @Override
    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return false;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_1_NANO.toString())
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 0
                    && request.id().equals(id)
                    && request.name().equals("getWeather")
                    && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
        }));
    }

    @Override
    protected boolean supportsPartialToolStreaming(StreamingChatModel model) {
        return false;
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 0
                    && request.id().equals(id1)
                    && request.name().equals("getWeather")
                    && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
        }));

        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 1
                    && request.id().equals(id2)
                    && request.name().equals("getTime")
                    && request.arguments().replace(" ", "").equals("{\"country\":\"France\"}");
        }));
    }

    @Override
    protected void should_respect_stopSequences_in_chat_request(StreamingChatModel model) {
        // Responses API does not support stopSequences parameter
    }

    @Override
    protected void should_respect_stopSequences_in_default_model_parameters() {
        // Responses API does not support stopSequences parameter
    }

    @Override
    protected void should_respect_maxOutputTokens_in_chat_request(StreamingChatModel model) {
        assertMaxOutputTokensRespected(model, 16, false);
    }

    @Override
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {
        assertMaxOutputTokensRespected(null, 16, true);
    }

    @Override
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            StreamingChatModel model) {
        assertMaxOutputTokensRespectedWithIntegrationSpecificParameters(model, 16, false);
    }

    @Override
    protected void
            should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters() {
        assertMaxOutputTokensRespectedWithIntegrationSpecificParameters(null, 16, true);
    }

    private void assertMaxOutputTokensRespected(
            StreamingChatModel model, int maxOutputTokens, boolean useDefaultParameters) {
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();

        if (useDefaultParameters) {
            model = createModelWith(parameters);
            if (model == null) {
                return;
            }
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a long story"))
                .parameters(useDefaultParameters ? null : parameters)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        var chatResponse = handler.get();

        assertMaxOutputTokensResponse(chatResponse, model, maxOutputTokens);
    }

    private void assertMaxOutputTokensRespectedWithIntegrationSpecificParameters(
            StreamingChatModel model, int maxOutputTokens, boolean useDefaultParameters) {
        ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);

        if (useDefaultParameters) {
            model = createModelWith(parameters);
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(parameters)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        var chatResponse = handler.get();

        assertMaxOutputTokensResponse(chatResponse, model, maxOutputTokens);
    }

    private void assertMaxOutputTokensResponse(
            ChatResponse chatResponse, StreamingChatModel model, int maxOutputTokens) {
        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            TokenUsage tokenUsage = chatResponse.metadata().tokenUsage();
            assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason())
                    .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        }
    }

    @Test
    void should_work_with_o_models() {
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("o4-mini")
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        assertThat(handler.get().aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_support_reasoning_effort() {
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("o4-mini")
                .reasoningEffort("medium")
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of France?", handler);

        assertThat(handler.get().aiMessage().text()).contains("Paris");
    }

    @Test
    void should_propagate_all_Responses_API_specific_parameters() {
        MockHttpClient mockHttpClient = new MockHttpClient();

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_4_1_NANO.toString())
                .temperature(0.7)
                .topP(0.9)
                .maxOutputTokens(100)
                .maxToolCalls(2)
                .parallelToolCalls(false)
                .topLogprobs(5)
                .truncation("auto")
                .include(List.of("message.output_text.logprobs", "reasoning.encrypted_content"))
                .strict(false)
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
    void should_send_tools_in_responses_api_format() throws Exception {
        MockHttpClient mockHttpClient = new MockHttpClient();

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_4_1_NANO.toString())
                .build();

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather data")
                .parameters(JsonObjectSchema.builder().addStringProperty("city").build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .toolSpecifications(toolSpecification)
                .build();

        try {
            model.chat(chatRequest, new TestStreamingChatResponseHandler());
        } catch (Exception ignored) {
        }

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode payload = objectMapper.readTree(mockHttpClient.request().body());
        JsonNode toolsNode = payload.get("tools");

        assertThat(toolsNode).isNotNull();
        assertThat(toolsNode.size()).isEqualTo(1);

        JsonNode toolNode = toolsNode.get(0);
        assertThat(toolNode.get("type").asText()).isEqualTo("function");
        assertThat(toolNode.get("name").asText()).isEqualTo("getWeather");
        assertThat(toolNode.get("description").asText()).isEqualTo("Get weather data");
        assertThat(toolNode.has("parameters")).isTrue();
        assertThat(toolNode.get("strict").asBoolean()).isTrue();

        assertThat(payload.get("tool_choice").asText()).isEqualTo("auto");
    }

    @Test
    void should_support_prompt_cache_retention() {
        MockHttpClient mockHttpClient = new MockHttpClient();

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_4_1_NANO.toString())
                .promptCacheRetention("24h")
                .build();

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("Hello")).build();

        try {
            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            model.chat(chatRequest, handler);
        } catch (Exception e) {
        }

        assertThat(mockHttpClient.request().body()).containsIgnoringWhitespaces("\"prompt_cache_retention\": \"24h\"");
    }

    @Test
    void should_support_custom_base_url() {
        MockHttpClient mockHttpClient = new MockHttpClient();

        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .modelName(GPT_4_1_NANO.toString())
                .baseUrl("https://custom.openai.example.com/v1")
                .build();

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("Hello")).build();

        try {
            TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
            model.chat(chatRequest, handler);
        } catch (Exception e) {
        }

        assertThat(mockHttpClient.request().url()).startsWith("https://custom.openai.example.com/v1/responses");
    }

    @Test
    void should_return_model_specific_response_metadata() {
        StreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_4_1_NANO.toString())
                .serviceTier("default")
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is 2+2?", handler);

        ChatResponseMetadata metadata = handler.get().metadata();
        assertThat(metadata).isInstanceOf(OpenAiChatResponseMetadata.class);

        OpenAiChatResponseMetadata openAiMetadata = (OpenAiChatResponseMetadata) metadata;
        assertThat(openAiMetadata.id()).isNotBlank();
        assertThat(openAiMetadata.modelName()).isNotBlank();

        if (openAiMetadata.tokenUsage() != null) {
            TokenUsage tokenUsage = openAiMetadata.tokenUsage();
            assertThat(tokenUsage).isInstanceOf(OpenAiTokenUsage.class);
            assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
            assertThat(tokenUsage.totalTokenCount())
                    .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        }
    }

    @Override
    protected void should_fail_if_images_as_public_URLs_are_not_supported(StreamingChatModel model) {
        UserMessage userMessage =
                UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(catImageUrl()));
        ChatRequest chatRequest = ChatRequest.builder().messages(userMessage).build();

        assertThatThrownBy(() -> chat(model, chatRequest));
    }

    @Override
    protected void should_respect_modelName_in_chat_request(StreamingChatModel model) {
        String modelName = customModelName();

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .modelName(modelName)
                .maxOutputTokens(16) // Responses API requires minimum of 16 tokens
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a story"))
                .parameters(parameters)
                .build();

        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        assertThat(chatResponse.aiMessage().text()).isNotBlank();
    }
}
