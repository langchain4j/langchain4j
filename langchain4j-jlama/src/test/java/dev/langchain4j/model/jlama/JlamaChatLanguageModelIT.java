package dev.langchain4j.model.jlama;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.assertj.core.util.Files;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class JlamaChatLanguageModelIT
{

    static File tmpDir;
    static ChatLanguageModel model;

    @BeforeAll
    static void setup() {
        tmpDir = Files.newTemporaryFolder();

        model = JlamaChatLanguageModel.builder()
                .modelName("tjake/TinyLlama-1.1B-Chat-v1.0-Jlama-Q4")
                .modelCachePath(tmpDir.toPath())
                .maxTokens(25)
                .build();
    }

    @Test
    void should_send_messages_and_return_response() {

        // given
        List<ChatMessage> messages = singletonList(UserMessage.from("hello"));

        // when
        Response<AiMessage> response = model.generate(messages);
        System.out.println(response);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNotBlank();

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
