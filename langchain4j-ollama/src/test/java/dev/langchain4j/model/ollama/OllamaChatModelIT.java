package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    ChatLanguageModel model = OllamaChatModel.builder()
            .baseUrl(ollama.getEndpoint())
            .modelName(TINY_DOLPHIN_MODEL)
            .temperature(0.0)
            .build();

    @Test
    void should_generate_response() {

        // given
        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).contains("Berlin");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(35);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .numPredict(numPredict)
                .temperature(0.0)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).doesNotContain("Berlin");
        assertThat(response.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict + 2); // bug in Ollama
    }

    @Test
    void should_respect_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("Translate messages from user into German");
        UserMessage userMessage = UserMessage.from("I love you");

        // when
        Response<AiMessage> response = model.generate(systemMessage, userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).containsIgnoringCase("liebe");
    }

    @Test
    void should_respond_to_few_shot() {

        // given
        List<ChatMessage> messages = asList(
                UserMessage.from("1 + 1 ="),
                AiMessage.from(">>> 2"),

                UserMessage.from("2 + 2 ="),
                AiMessage.from(">>> 4"),

                UserMessage.from("4 + 4 =")
        );

        // when
        Response<AiMessage> response = model.generate(messages);
        System.out.println(response);

        // then
        assertThat(response.content().text()).startsWith(">>> 8");
    }

    @Test
    void should_generate_valid_json() {

        // given
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .format("json")
                .temperature(0.0)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        String json = model.generate(userMessage);

        // then
        assertThat(json).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
    }

    @Test
    void should_preload_model_if_preload_is_true() {
        // given
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .preload(true)
                .build();

        // when
        // Preload is called inside the constructor if preload is true, so no action is needed here.

        // then
        // Check if preload was called by verifying if a dummy request was made
        // This might require you to mock the underlying OllamaClient and verify that `generate` was called with an empty message
        // Assuming `OllamaClient` is mockable and you have a way to inspect interactions:
        assertThat(model.modelLoadedInMemory).isTrue();
    }

    @Test
    void should_not_preload_model_if_preload_is_false() {
        // given
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .preload(false)
                .build();

        // when
        // Preload is called inside the constructor if preload is true, so no action is needed here.

        // then
        // Check if preload was called by verifying if a dummy request was made
        // This might require you to mock the underlying OllamaClient and verify that `generate` was called with an empty message
        // Assuming `OllamaClient` is mockable and you have a way to inspect interactions:
        assertThat(model.modelLoadedInMemory).isFalse();
    }

    @Test
    void should_pass_keep_alive_parameter() {
        // given
        String keepAliveDuration = "10m";
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(TINY_DOLPHIN_MODEL)
                .temperature(0.0)
                .keepAlive(keepAliveDuration)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");
    }
}

