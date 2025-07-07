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
        String modelName = "meta-llama/Llama-3.2-11B-Vision-Instruct";
        String baseUrl = String.format("https://router.huggingface.co/hf-inference/models/%s/v1", modelName);

        ChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("HF_API_KEY"))
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();

        // when
        String answer = model.chat("What is the capital of Germany?");

        // then
        assertThat(answer).containsIgnoringCase("Berlin");
    }
}
