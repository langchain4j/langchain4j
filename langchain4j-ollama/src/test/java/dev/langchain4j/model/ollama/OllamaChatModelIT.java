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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaChatModelIT extends AbstractOllamaInfrastructure {

    ChatLanguageModel model = OllamaChatModel.builder()
            .baseUrl(getBaseUrl())
            .modelName(MODEL)
            .temperature(0.0)
            .build();

    @Test
    void should_generate_answer() {

        // given
        String userMessage = "What is the capital of Germany?";

        // when
        String answer = model.generate(userMessage);
        System.out.println(answer);

        // then
        assertThat(answer).contains("Berlin");
    }

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(getBaseUrl())
                .modelName(MODEL)
                .numPredict(numPredict)
                .temperature(0.0)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).doesNotContain("Berlin");
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(numPredict + 2); // bug in Ollama
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

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(18);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
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
        assertThat(response.content().text()).isEqualTo(">>> 8");
    }

    @Test
    void should_generate_valid_json() {

        // given
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(getBaseUrl())
                .modelName(MODEL)
                .format("json")
                .temperature(0.0)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        String json = model.generate(userMessage);

        // then
        assertThat(json).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
    }
}