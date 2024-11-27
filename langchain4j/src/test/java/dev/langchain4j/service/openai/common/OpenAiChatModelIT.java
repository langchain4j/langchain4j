package dev.langchain4j.service.openai.common;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiChatModelIT extends AbstractChatModelIT {

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

    @Test
    void should_respect_logitBias_parameter() {

        // given
        OpenAiChatModel model = OPEN_AI_CHAT_MODEL_BUILDER
                .maxTokens(20) // to save tokens
                .build();

        Map<String, Integer> logitBias = Map.of(
                "72782", 100 // token ID for "Paris", see https://platform.openai.com/tokenizer -> "Token IDs"
        );

        OpenAiChatParameters parameters = OpenAiChatParameters.builder()
                .logitBias(logitBias)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(parameters)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text())
                .containsIgnoringCase("Paris")
                .doesNotContainIgnoringCase("Berlin");
    }

    // TODO test all OpenAI parameters
}
