package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

class OllamaResponsesChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    static final String MODEL_NAME = TINY_DOLPHIN_MODEL;

    @Test
    void should_respond_to_simple_question() {
        // given
        OllamaResponsesChatModel model = OllamaResponsesChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is the capital of Germany?"));

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Berlin");
        ChatResponseMetadata metadata = response.metadata();
        assertThat(metadata.modelName()).isEqualTo(MODEL_NAME);
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(metadata.tokenUsage()).isNotNull();
        assertThat(metadata.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(metadata.tokenUsage().outputTokenCount()).isGreaterThan(0);
    }

    @Test
    void should_respect_maxOutputTokens() {
        // given
        OllamaResponsesChatModel model = OllamaResponsesChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .maxOutputTokens(1)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is the capital of Germany?"));

        // then
        // Ollama returns status "completed" even when truncated by max_output_tokens,
        // so we verify the output is short rather than checking for LENGTH finish reason
        assertThat(response.aiMessage().text()).isNotNull();
        ChatResponseMetadata metadata = response.metadata();
        assertThat(metadata.tokenUsage()).isNotNull();
        assertThat(metadata.tokenUsage().outputTokenCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void should_work_with_instructions() {
        // given
        OllamaResponsesChatModel model = OllamaResponsesChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .instructions("You are a helpful assistant that always responds in French.")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is your name?"));

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
        ChatResponseMetadata metadata = response.metadata();
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
    }
}
