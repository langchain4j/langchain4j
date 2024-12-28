package dev.langchain4j.service.common.openai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    public static OpenAiChatModel.OpenAiChatModelBuilder defaultModelBuilder() {
        return OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true);
    }

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                defaultModelBuilder()
                        .build(),
                defaultModelBuilder()
                        .strictTools(true)
                        .build(),
                defaultModelBuilder()
                        .responseFormat("json_schema")
                        .strictJsonSchema(true)
                        .build()
                // TODO json_object?
        );
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        return OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .defaultRequestParameters(parameters)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Test
    void should_respect_logitBias_parameter() {

        // given
        Map<String, Integer> logitBias = Map.of(
                "72782", 100 // token ID for "Paris", see https://platform.openai.com/tokenizer -> "Token IDs"
        );

        OpenAiChatRequestParameters openAiParameters = OpenAiChatRequestParameters.builder()
                .logitBias(logitBias)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(openAiParameters)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        ChatLanguageModel chatModel = defaultModelBuilder()
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

        ChatRequest.Builder chatRequestBuilder = ChatRequest.builder()
                .messages(UserMessage.from("How much is 2+2 and 3+3?"));

        ChatLanguageModel chatModel = defaultModelBuilder()
                .build();

        // when parallelToolCalls = true
        OpenAiChatRequestParameters openAiParameters = OpenAiChatRequestParameters.builder()
                .toolSpecifications(toolSpecification)
                .parallelToolCalls(true)
                .build();
        ChatRequest chatRequest = chatRequestBuilder.parameters(openAiParameters)
                .build();
        ChatResponse chatResponse = chatModel.chat(chatRequest);
        // then
        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(2);

        // when parallelToolCalls = false
        OpenAiChatRequestParameters openAiChatParameters2 = OpenAiChatRequestParameters.builder()
                .toolSpecifications(toolSpecification)
                .parallelToolCalls(false)
                .build();
        ChatRequest chatRequest2 = chatRequestBuilder.parameters(openAiChatParameters2)
                .build();
        ChatResponse chatResponse2 = chatModel.chat(chatRequest2);
        // then
        assertThat(chatResponse2.aiMessage().toolExecutionRequests()).hasSize(1);
    }

    @Test
    void should_propagate_all_OpenAI_specific_parameters() {

        // given
        OpenAiChatRequestParameters openAiParameters = OpenAiChatRequestParameters.builder()
                .seed(12345)
                .user("Klaus")
                .store(true)
                .metadata(Map.of(
                        "one", "1",
                        "two", "2"
                ))
                .serviceTier("default")
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(openAiParameters)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        ChatLanguageModel chatModel = defaultModelBuilder()
                .logRequests(true) // verifying manually in the logs for now
                .logResponses(true) // verifying manually in the logs for now
                .build();

        // when
        ChatResponse chatResponse = chatModel.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text()).containsIgnoringCase("Berlin");

        // TODO verify that parameters are propagated after https://github.com/langchain4j/langchain4j/issues/1044
    }

    @Test
    void should_respect_default_common_chat_parameters() {

        // given
        int maxOutputTokens = 3;
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();

        ChatLanguageModel chatModel = defaultModelBuilder()
                .defaultRequestParameters(parameters)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponse chatResponse = chatModel.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.metadata().tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
        }
    }

    @Test
    void should_return_model_specific_response_metadata() {

        // given
        int maxOutputTokens = 1;
        String serviceTier = "default";

        OpenAiChatRequestParameters openAiParameters = OpenAiChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens) // to save tokens
                .serviceTier(serviceTier) // required to get the "serviceTier" attribute in the response
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(openAiParameters)
                .messages(UserMessage.from("Hi"))
                .build();

        OpenAiChatModel chatModel = defaultModelBuilder()
                .build();

        // when
        ChatResponse chatResponse = chatModel.chat(chatRequest);

        // then
        OpenAiChatResponseMetadata openAiChatResponseMetadata = (OpenAiChatResponseMetadata) chatResponse.metadata();
        assertThat(openAiChatResponseMetadata.created()).isPositive();
        assertThat(openAiChatResponseMetadata.serviceTier()).isEqualTo(serviceTier);
        assertThat(openAiChatResponseMetadata.systemFingerprint()).isNotBlank();

        OpenAiTokenUsage tokenUsage = openAiChatResponseMetadata.tokenUsage();

        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isZero();

        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        assertThat(tokenUsage.outputTokensDetails().reasoningTokens()).isZero();

        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
