package dev.langchain4j.model.openai.common;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_5_MINI;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    public static OpenAiChatModel.OpenAiChatModelBuilder defaultModelBuilder() {
        return OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_5_MINI)
                .logRequests(false) // images are huge in logs
                .logResponses(true);
    }

    @Override
    protected List<ChatModel> models() {
        return List.of(
                defaultModelBuilder().build(),
                defaultModelBuilder()
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(true)
                        .build(),
                defaultModelBuilder()
                        .responseFormat("json_schema") // testing backward compatibility
                        .strictJsonSchema(true)
                        .build()
                // TODO json_object?
        );
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        OpenAiChatModel.OpenAiChatModelBuilder openAiChatModelBuilder = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .defaultRequestParameters(adjustForGpt5(parameters))
                .logRequests(true)
                .logResponses(true);
        if (parameters.modelName() == null) {
            openAiChatModelBuilder.modelName(GPT_5_MINI);
        }
        return openAiChatModelBuilder.build();
    }

    static ChatRequestParameters adjustForGpt5(ChatRequestParameters parameters) {
        // GPT-5 does not support maxOutputTokens, need to use maxCompletionTokens instead
        return OpenAiChatRequestParameters.builder()
                .overrideWith(parameters)
                .maxOutputTokens(null)
                .maxCompletionTokens(parameters.maxOutputTokens())
                .build();
    }

    @Override
    protected int maxOutputTokens() {
        return 1000;
    }

    @Override
    protected ChatRequestParameters createParameters(int maxOutputTokens) {
        // GPT-5 does not support maxOutputTokens, need to use maxCompletionTokens instead
        return createIntegrationSpecificParameters(maxOutputTokens);
    }

    @Override
    protected void assertOutputTokenCount(TokenUsage tokenUsage, Integer maxOutputTokens) {
        OpenAiTokenUsage openAiTokenUsage = (OpenAiTokenUsage) tokenUsage;
        assertThat(tokenUsage.outputTokenCount() - openAiTokenUsage.outputTokensDetails().reasoningTokens())
                .isLessThanOrEqualTo(maxOutputTokens);
    }

    @Override
    protected Set<FinishReason> finishReasonForMaxOutputTokens() {
        return Set.of(LENGTH, STOP); // It is hard to make GPT-5 to hit LENGTH because of unpredictable reasoning
    }

    @Override
    protected String customModelName() {
        return "gpt-5-nano-2025-08-07";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiChatRequestParameters.builder()
                .maxCompletionTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel chatModel) {
        return OpenAiChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel chatModel) {
        return OpenAiTokenUsage.class;
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // GPT-5 does not support stop sequences
    }

    @Override
    protected void should_fail_if_stopSequences_parameter_is_not_supported(ChatModel model) {
        // GPT-5 does not support stop sequences, but other models do support
    }

    @Override
    protected String catImageUrl() {
        return "https://images.all-free-download.com/images/graphicwebp/cat_hangover_relax_213869.webp";
    }

    @Override
    protected String diceImageUrl() {
        return "https://images.all-free-download.com/images/graphicwebp/double_six_dice_196084.webp";
    }

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters.overrideWith(OpenAiChatRequestParameters.builder().reasoningEffort("low").build());
    }

    @Test
    void should_respect_logitBias_parameter() {

        // given
        Map<String, Integer> logitBias = Map.of(
                "72782", 100 // token ID for "Paris", see https://platform.openai.com/tokenizer -> "Token IDs"
        );

        OpenAiChatRequestParameters openAiParameters =
                OpenAiChatRequestParameters.builder().logitBias(logitBias).build();

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(openAiParameters)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        ChatModel chatModel = defaultModelBuilder()
                .modelName(GPT_4_O_MINI)
                .maxTokens(20) // to save tokens
                .build();

        // when
        ChatResponse chatResponse = chatModel.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text())
                .containsIgnoringCase("Paris")
                .doesNotContainIgnoringCase("Berlin");
    }

    @Test
    void should_respect_parallelToolCalls_parameter() {

        // given
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("add")
                .description("adds two numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addNumberProperty("b")
                        .required("a", "b")
                        .build())
                .build();

        ChatRequest.Builder chatRequestBuilder =
                ChatRequest.builder().messages(UserMessage.from("How much is 2+2 and 3+3?"));

        ChatModel chatModel = defaultModelBuilder()
                .modelName(GPT_4_O_MINI)
                .build();

        // when parallelToolCalls = true
        OpenAiChatRequestParameters openAiParameters = OpenAiChatRequestParameters.builder()
                .toolSpecifications(toolSpecification)
                .parallelToolCalls(true)
                .build();
        ChatRequest chatRequest =
                chatRequestBuilder.parameters(openAiParameters).build();
        ChatResponse chatResponse = chatModel.chat(chatRequest);
        // then
        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(2);

        // when parallelToolCalls = false
        OpenAiChatRequestParameters openAiChatParameters2 = OpenAiChatRequestParameters.builder()
                .toolSpecifications(toolSpecification)
                .parallelToolCalls(false)
                .build();
        ChatRequest chatRequest2 =
                chatRequestBuilder.parameters(openAiChatParameters2).build();
        ChatResponse chatResponse2 = chatModel.chat(chatRequest2);
        // then
        assertThat(chatResponse2.aiMessage().toolExecutionRequests()).hasSize(1);
    }

    @Test
    void should_propagate_all_OpenAI_specific_parameters() {

        // given
        OpenAiChatRequestParameters openAiParameters = OpenAiChatRequestParameters.builder()
                .maxCompletionTokens(123)
                .seed(12345)
                .user("Klaus")
                .store(true)
                .metadata(Map.of("key", "value"))
                .serviceTier("default")
                .reasoningEffort("medium")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(openAiParameters)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        MockHttpClient mockHttpClient = new MockHttpClient();

        ChatModel chatModel = defaultModelBuilder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .maxRetries(0) // it will fail, so no need to retry
                .build();

        // when
        try {
            chatModel.chat(chatRequest);
        } catch (Exception e) {
            // it fails because MockHttpClient.execute() returns null
        }

        // then
        assertThat(mockHttpClient.request().body())
                .containsIgnoringWhitespaces("\"seed\": 12345")
                .containsIgnoringWhitespaces("\"user\": \"Klaus\"")
                .containsIgnoringWhitespaces("\"store\": true")
                .containsIgnoringWhitespaces("\"metadata\": {\"key\": \"value\"}")
                .containsIgnoringWhitespaces("\"service_tier\": \"default\"")
                .containsIgnoringWhitespaces("\"reasoning_effort\": \"medium\"");
    }

    @Test
    void should_return_model_specific_response_metadata() {

        // given
        String serviceTier = "default";

        OpenAiChatRequestParameters parameters = OpenAiChatRequestParameters.builder()
                .serviceTier(serviceTier) // required to get the "serviceTier" attribute in the response
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(saveTokens(parameters))
                .messages(UserMessage.from("Hi"))
                .build();

        OpenAiChatModel chatModel = defaultModelBuilder().build();

        // when
        ChatResponse chatResponse = chatModel.chat(chatRequest);

        // then
        OpenAiChatResponseMetadata openAiChatResponseMetadata = (OpenAiChatResponseMetadata) chatResponse.metadata();
        assertThat(openAiChatResponseMetadata.created()).isPositive();
        assertThat(openAiChatResponseMetadata.serviceTier()).isEqualTo(serviceTier);
        // assertThat(openAiChatResponseMetadata.systemFingerprint()).isNotBlank(); OpenAI stopped providing it

        OpenAiTokenUsage tokenUsage = openAiChatResponseMetadata.tokenUsage();

        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isZero();

        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokensDetails().reasoningTokens()).isNotNegative();
    }

    @Test
    void should_propagate_custom_http_headers() {

        // given
        Map<String, String> customHeaders = Map.of(
                "key1", "value1",
                "key2", "value2");

        MockHttpClient mockHttpClient = new MockHttpClient();

        ChatModel chatModel = defaultModelBuilder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .customHeaders(customHeaders)
                .maxRetries(0) // it will fail, so no need to retry
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        try {
            chatModel.chat(chatRequest);
        } catch (Exception e) {
            // it fails because MockHttpClient.execute() returns null
        }

        // then
        assertThat(mockHttpClient.request().headers())
                .containsEntry("key1", List.of("value1"))
                .containsEntry("key2", List.of("value2"));
    }

    @Test
    void should_propagate_custom_query_parameters() {

        // given
        Map<String, String> customQueryParams = new LinkedHashMap<>();
        customQueryParams.put("param1", "value1");
        customQueryParams.put("param2", "value2");

        MockHttpClient mockHttpClient = new MockHttpClient();

        ChatModel chatModel = defaultModelBuilder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://localhost/v1")
                .customQueryParams(customQueryParams)
                .maxRetries(0) // it will fail, so no need to retry
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        try {
            chatModel.chat(chatRequest);
        } catch (Exception e) {
            // it fails because MockHttpClient.execute() returns null
        }

        // then
        assertThat(mockHttpClient.request().url())
                .isEqualTo("https://localhost/v1/chat/completions?param1=value1&param2=value2");
    }
}
