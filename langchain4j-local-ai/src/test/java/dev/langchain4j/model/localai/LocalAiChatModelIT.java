package dev.langchain4j.model.localai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiChatModelIT extends AbstractLocalAiInfrastructure {

    @Test
    void should_send_user_message_and_return_answer() {

        ChatLanguageModel model = LocalAiChatModel.builder()
                .baseUrl(localAi.getBaseUrl())
                .modelName("ggml-gpt4all-j")
                .maxTokens(3)
                .logRequests(true)
                .logResponses(true)
                .build();

        String answer = model.generate("Say 'hello'");

        assertThat(answer).isNotBlank();
        System.out.println(answer);
    }
}