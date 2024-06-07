package dev.langchain4j.model;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.LambdaStreamingResponseHandler.onNext;
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onNextAndError;

public class LambdaStreamingResponseHandlerTest implements WithAssertions {
    @Test
    void testOnNext() {
        // given
        List tokens = new ArrayList<>();
        tokens.add("The sky ");
        tokens.add("is blue because of ");
        tokens.add("a phenomenon called ");
        tokens.add("Rayleigh scattering.");

        StreamingChatLanguageModel model = new DummyModel(tokens);

        // when
        List receivedTokens = new ArrayList<>();
        model.generate("Why is the sky blue?",
            onNext(text -> receivedTokens.add(text)));

        // then
        assertThat(receivedTokens).containsSequence(tokens);
    }

    @Test
    void testOnNextAndError() {
        // given
        List tokens = new ArrayList<>();
        tokens.add("Three ");
        tokens.add("Two ");
        tokens.add("One ");
        tokens.add(new RuntimeException("BOOM"));

        StreamingChatLanguageModel model = new DummyModel(tokens);

        // when
        List receivedTokens = new ArrayList<>();
        final Throwable[] thrown = { null };

        model.generate("Create a countdown",
            onNextAndError(text -> receivedTokens.add(text), t -> thrown[0] = t));

        // then
        assertThat(tokens).containsSubsequence(receivedTokens);
        assertThat(thrown[0]).isNotNull();
        assertThat(thrown[0]).isInstanceOf(RuntimeException.class);
        assertThat(((Throwable)thrown[0]).getMessage()).isEqualTo("BOOM");
    }

    class DummyModel implements StreamingChatLanguageModel {
        private final List stringsAndError;

        public DummyModel(List stringsAndError) {
            this.stringsAndError = stringsAndError;
        }

        @Override
        public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
            StreamingChatLanguageModel.super.generate(userMessage, handler);
        }

        @Override
        public void generate(UserMessage userMessage, StreamingResponseHandler<AiMessage> handler) {
            StreamingChatLanguageModel.super.generate(userMessage, handler);
        }

        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            stringsAndError.forEach(obj -> {
                if (obj instanceof String) {
                    String msg = (String)obj;
                    handler.onNext(msg);
                } else if (obj instanceof Throwable) {
                    Throwable problem = (Throwable) obj;
                    handler.onError(problem);
                }
            });
        }

        @Override
        public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
            StreamingChatLanguageModel.super.generate(messages, toolSpecifications, handler);
        }

        @Override
        public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
            StreamingChatLanguageModel.super.generate(messages, toolSpecification, handler);
        }
    }
}
