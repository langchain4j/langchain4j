package dev.langchain4j.model.jlama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class JlamaChatModelIT {

    static File tmpDir;
    static ChatModel model;

    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        model = JlamaChatModel.builder()
                .modelName("tjake/Llama-3.2-1B-Instruct-JQ4")
                .modelCachePath(tmpDir.toPath())
                .temperature(0.0f)
                .maxTokens(64)
                .build();
    }

    @Test
    void should_send_messages_and_return_response() {

        // given
        List<ChatMessage> messages = singletonList(UserMessage.from("When is the best time of year to visit Japan?"));

        // when
        ChatResponse response = model.chat(messages);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
