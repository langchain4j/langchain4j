package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests if Ollama can be used via OpenAI API (langchain4j-open-ai module)
 * See https://github.com/ollama/ollama/blob/main/docs/openai.md
 */
class OllamaOpenAiStreamingChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    StreamingChatModel model = OpenAiStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama) + "/v1")
            .modelName(TINY_DOLPHIN_MODEL)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_generate_response() {

        // given
        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).contains("Berlin");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        OpenAiTokenUsage tokenUsage = (OpenAiTokenUsage) response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.inputTokensDetails()).isNull();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokensDetails()).isNull();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    // TODO add more tests
}
