package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class StreamingChatLanguageModelTest implements WithAssertions {
    public static class StreamingUpperCaseEchoModel implements StreamingChatLanguageModel {
        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            Response<AiMessage> response = new Response<>(new AiMessage(lastMessage.text().toUpperCase(Locale.ROOT)));
            handler.onComplete(response);
        }
    }

    public static final class CollectorResponseHandler<T> implements StreamingResponseHandler<T> {
        private final List<Response<T>> responses = new ArrayList<>();

        public List<Response<T>> responses() {
            return responses;
        }

        @Override
        public void onNext(String token) {}

        @Override
        public void onError(Throwable error) {}

        @Override
        public void onComplete(Response<T> response) {
            responses.add(response);
        }
    }

    @Test
    public void test_not_supported() {
        StreamingUpperCaseEchoModel model = new StreamingUpperCaseEchoModel();
        CollectorResponseHandler<AiMessage> handler = new CollectorResponseHandler<>();
        List<ChatMessage> messages = new ArrayList<>();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> model.generate(messages, new ArrayList<>(), handler))
                .withMessageContaining("Tools are currently not supported by this model");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> model.generate(messages, ToolSpecification.builder().name("foo").build(), handler))
                .withMessageContaining("Tools are currently not supported by this model");
    }

    @Test
    public void test_generate() {
        StreamingChatLanguageModel model = new StreamingUpperCaseEchoModel();

        {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello"));
            messages.add(new AiMessage("Hi"));
            messages.add(new UserMessage("How are you?"));

            CollectorResponseHandler<AiMessage> handler = new CollectorResponseHandler<>();
            model.generate(messages, handler);

            Response<AiMessage> response = handler.responses().get(0);

            assertThat(response.content().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }

        {
            CollectorResponseHandler<AiMessage> handler = new CollectorResponseHandler<>();
            model.generate("How are you?", handler);

            Response<AiMessage> response = handler.responses().get(0);

            assertThat(response.content().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }
    }
}