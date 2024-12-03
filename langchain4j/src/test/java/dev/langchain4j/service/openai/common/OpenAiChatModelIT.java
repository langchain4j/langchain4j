package dev.langchain4j.service.openai.common;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequest;
import dev.langchain4j.model.openai.OpenAiChatResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    static final OpenAiChatModel.OpenAiChatModelBuilder OPEN_AI_CHAT_MODEL_BUILDER = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI);

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OPEN_AI_CHAT_MODEL_BUILDER
                        .build(),
                OPEN_AI_CHAT_MODEL_BUILDER
                        .strictTools(true)
                        .build(),
                OPEN_AI_CHAT_MODEL_BUILDER
                        .responseFormat("json_schema")
                        .strictJsonSchema(true)
                        .build()
                // TODO json_object?
        );
    }

    @Override
    protected String modelName() {
        return "gpt-4o-2024-11-20";
    }

    @Override
    protected ChatRequest createModelSpecificChatRequest(int maxOutputTokens, UserMessage userMessage) {
        return OpenAiChatRequest.builder()
                .maxOutputTokens(maxOutputTokens)
                .messages(userMessage)
                .build();
    }

    @Test
    void should_respect_logitBias_parameter() {

        // given
        OpenAiChatModel chatModel = OPEN_AI_CHAT_MODEL_BUILDER
                .maxTokens(20) // to save tokens
                .build();

        Map<String, Integer> logitBias = Map.of(
                "72782", 100 // token ID for "Paris", see https://platform.openai.com/tokenizer -> "Token IDs"
        );

        OpenAiChatRequest chatRequest = OpenAiChatRequest.builder()
                .logitBias(logitBias)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        OpenAiChatResponse chatResponse = chatModel.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text())
                .containsIgnoringCase("Paris")
                .doesNotContainIgnoringCase("Berlin");
    }

    @Test
    void should_respect_parallelToolCalls_parameter() {

        // given
        OpenAiChatModel chatModel = OPEN_AI_CHAT_MODEL_BUILDER
                .build();

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("add")
                .description("adds two numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addNumberProperty("b")
                        .required("a", "b")
                        .build())
                .build();

        OpenAiChatRequest.Builder chatRequestBuilder = OpenAiChatRequest.builder()
                .messages(UserMessage.from("How much is 2+2 and 3+3?"))
                .toolSpecifications(toolSpecification);

        // when
        OpenAiChatResponse chatResponse = chatModel.chat(chatRequestBuilder.parallelToolCalls(true).build());
        // then
        assertThat(chatResponse.aiMessage().toolExecutionRequests()).hasSize(2);

        // when
        OpenAiChatResponse chatResponse2 = chatModel.chat(chatRequestBuilder.parallelToolCalls(false).build());
        // then
        assertThat(chatResponse2.aiMessage().toolExecutionRequests()).hasSize(1);
    }

    @Test
    void should_propagate_all_OpenAI_parameters() {

        // given
        OpenAiChatModel chatModel = OPEN_AI_CHAT_MODEL_BUILDER
                .logRequests(true) // verifying manually in the logs for now
                .logResponses(true)
                .build();

        OpenAiChatRequest chatRequest = OpenAiChatRequest.builder()
                .seed(12345)
                .user("Klaus")
                .store(true)
                .metadata(Map.of(
                        "one", "1",
                        "two", "2"
                ))
                .serviceTier("default")
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        OpenAiChatResponse chatResponse = chatModel.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text()).containsIgnoringCase("Berlin");

        // TODO verify that parameters are propagated after https://github.com/langchain4j/langchain4j/issues/1044
    }

    @Test
    void should_return_custom_response() {

        // given
        OpenAiChatModel chatModel = OPEN_AI_CHAT_MODEL_BUILDER
                .build();

        int maxOutputTokens = 1;
        String serviceTier = "default";

        OpenAiChatRequest chatRequest = OpenAiChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .maxOutputTokens(maxOutputTokens) // to save tokens
                .serviceTier(serviceTier) // required to get the "serviceTier" attribute in the response
                .build();

        // when
        OpenAiChatResponse chatResponse = chatModel.chat(chatRequest);

        // then
        assertThat(chatResponse.created()).isPositive();
        assertThat(chatResponse.serviceTier()).isEqualTo(serviceTier);
        assertThat(chatResponse.systemFingerprint()).isNotBlank();

        OpenAiTokenUsage tokenUsage = chatResponse.tokenUsage();

        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isZero();

        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        assertThat(tokenUsage.outputTokensDetails().reasoningTokens()).isZero();

        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
