package dev.langchain4j.model.huggingface;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "HF_API_KEY", matches = ".+")
class HuggingFaceStreamingChatModelIT {

    @Test
    void should_send_messages_and_receive_response() {

        // given
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .apiKey(System.getenv("HF_API_KEY"))
                .baseUrl("https://router.huggingface.co/v1")
                .modelName("HuggingFaceTB/SmolLM3-3B:hf-inference")
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).containsIgnoringCase("Berlin");
    }
}
