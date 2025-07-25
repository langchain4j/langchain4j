package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaClientThinkingIT extends AbstractOllamaThinkingModelInfrastructure {

    Message whySkyIsBlueMessage =
            Message.builder().role(Role.USER).content("Why sky is blue?").build();

    OllamaClient ollamaClient = OllamaClient.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .logRequests(true)
            .logResponses(true)
            .timeout(Duration.ofMinutes(1))
            .build();

    @Test
    void should_respond_with_thinking_when_think_is_true() {
        // given AbstractOllamaThinkingModelInfrastructure

        // when
        OllamaChatResponse ollamaChatResponse = ollamaClient.chat(OllamaChatRequest.builder().think(true).stream(false)
                .model(MODEL_NAME)
                .messages(List.of(whySkyIsBlueMessage))
                .build());

        assertThat(ollamaChatResponse.getMessage().getThinking()).isNotEmpty();
        assertThat(ollamaChatResponse.getMessage().getContent()).isNotEmpty();
    }

    @Test
    void should_respond_with_no_thinking_when_think_is_false() {
        // given AbstractOllamaThinkingModelInfrastructure

        // when
        OllamaChatResponse ollamaChatResponse = ollamaClient.chat(OllamaChatRequest.builder().think(false).stream(false)
                .model(MODEL_NAME)
                .messages(List.of(whySkyIsBlueMessage))
                .build());

        assertThat(ollamaChatResponse.getMessage().getThinking()).isNullOrEmpty();
        assertThat(ollamaChatResponse.getMessage().getContent()).isNotEmpty();
    }

    @Test
    void should_respond_with_thinking_block_in_content_when_think_is_not_set() {
        // given AbstractOllamaThinkingModelInfrastructure

        // when
        OllamaChatResponse ollamaChatResponse = ollamaClient.chat(OllamaChatRequest.builder().stream(false)
                .model(MODEL_NAME)
                .messages(List.of(whySkyIsBlueMessage))
                .build());

        assertThat(ollamaChatResponse.getMessage().getThinking()).isNullOrEmpty();
        assertThat(ollamaChatResponse.getMessage().getContent()).isNotEmpty();
        assertThat(ollamaChatResponse.getMessage().getContent()).contains("<think>");
        assertThat(ollamaChatResponse.getMessage().getContent()).contains("</think>");
    }
}
