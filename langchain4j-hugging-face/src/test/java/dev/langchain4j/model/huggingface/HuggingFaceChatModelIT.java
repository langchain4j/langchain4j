package dev.langchain4j.model.huggingface;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "HF_API_KEY", matches = ".+")
class HuggingFaceChatModelIT {

    @Test
    void should_send_messages_and_receive_response() {

        // given
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("HF_API_KEY"))
                .baseUrl("https://router.huggingface.co/v1")
                .modelName("HuggingFaceTB/SmolLM3-3B:hf-inference")
                .build();

        // when
        String answer = model.chat("What is the capital of Germany?");

        // then
        assertThat(answer).containsIgnoringCase("Berlin");
    }
}
