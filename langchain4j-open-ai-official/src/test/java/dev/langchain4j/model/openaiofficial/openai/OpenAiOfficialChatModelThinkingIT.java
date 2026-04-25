package dev.langchain4j.model.openaiofficial.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialChatModelThinkingIT {

    @Test
    void should_return_thinking() {

        // given
        ChatModel model = createModel(true);

        // when
        ChatResponse chatResponse = model.chat(UserMessage.from("What is the capital of Germany?"));

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNotBlank();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void should_not_return_thinking(Boolean returnThinking) {

        // given
        ChatModel model = createModel(returnThinking);

        // when
        ChatResponse chatResponse = model.chat(UserMessage.from("What is the capital of Germany?"));

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.thinking()).isNull();
    }

    private static ChatModel createModel(Boolean returnThinking) {
        return OpenAiOfficialChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("o4-mini")
                .defaultRequestParameters(OpenAiOfficialChatRequestParameters.builder()
                        .reasoningEffort("medium")
                        .build())
                .returnThinking(returnThinking)
                .build();
    }
}
