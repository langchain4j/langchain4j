package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ChatLanguageModelTest implements WithAssertions {
    public static class UpperCaseEchoModel implements ChatLanguageModel {
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            return new Response<>(new AiMessage(lastMessage.text().toUpperCase(Locale.ROOT)));
        }
    }

    @Test
    public void test_not_supported() {
        ChatLanguageModel model = new UpperCaseEchoModel();

        List<ChatMessage> messages = new ArrayList<>();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> model.generate(messages, new ArrayList<>()))
                .withMessageContaining("Tools are currently not supported by this model");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> model.generate(messages, ToolSpecification.builder().name("foo").build()))
                .withMessageContaining("Tools are currently not supported by this model");
    }

    @Test
    public void test_generate() {
        ChatLanguageModel model = new UpperCaseEchoModel();

        assertThat(model.generate("how are you?"))
                .isEqualTo("HOW ARE YOU?");

        {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello"));
            messages.add(new AiMessage("Hi"));
            messages.add(new UserMessage("How are you?"));

            Response<AiMessage> response = model.generate(messages);

            assertThat(response.content().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }

        {
            Response<AiMessage> response = model.generate(
                    new UserMessage("Hello"),
                    new AiMessage("Hi"),
                    new UserMessage("How are you?"));

            assertThat(response.content().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }
    }
}
