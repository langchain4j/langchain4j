package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaChatModelIT extends AbstractOllamaInfrastructure {

    ChatLanguageModel chatModel = OllamaChatModel.builder()
            .baseUrl(getBaseUrl())
            .modelName(ORCA_MINI_MODEL)
            .build();

    @Test
    void should_generate_answer() {

        String prompt = "Hello, how are you?";

        Response<AiMessage> response = chatModel.generate(UserMessage.from(prompt));
        System.out.println(response);

        assertThat(response.content().text()).isNotBlank();
        assertThat(response.tokenUsage()).isNotNull();
    }
}
