package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaImage.MAGISTRAL_24B;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

class OllamaThinkChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    static final String MODEL_NAME = MAGISTRAL_24B;

    @Test
    void should_think() {

        StreamingChatModel model = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(MODEL_NAME)
            .temperature(0.0)
            .think(true)
            .logRequests(true)
            .logResponses(true)
            .build();

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();
        String answer = response.aiMessage().text();

        // then
        assertThat(answer).contains("Berlin");

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isEqualTo(answer);
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        ChatResponseMetadata metadata = response.metadata();

        assertThat(metadata.modelName()).isEqualTo(MODEL_NAME);

        TokenUsage tokenUsage = metadata.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_not_think() {

        StreamingChatModel model = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(MODEL_NAME)
            .temperature(0.0)
            .think(false)
            .logRequests(true)
            .logResponses(true)
            .build();

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();
        String answer = response.aiMessage().text();

        // then
        assertThat(answer).contains("Berlin");

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isEqualTo(answer);
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        ChatResponseMetadata metadata = response.metadata();

        assertThat(metadata.modelName()).isEqualTo(MODEL_NAME);

        TokenUsage tokenUsage = metadata.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
    }
}
