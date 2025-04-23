package dev.langchain4j.model;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponse;
import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponseAndError;

class LambdaStreamingResponseHandlerTest implements WithAssertions {

    @Test
    void testOnPartialResponse() {
        // given
        List<Object> tokens = new ArrayList<>();
        tokens.add("The sky ");
        tokens.add("is blue because of ");
        tokens.add("a phenomenon called ");
        tokens.add("Rayleigh scattering.");

        StreamingChatModel model = new DummyModel(tokens);

        // when
        List<Object> receivedTokens = new ArrayList<>();
        model.chat("Why is the sky blue?", onPartialResponse(receivedTokens::add));

        // then
        assertThat(receivedTokens).containsSequence(tokens);
    }

    @Test
    void testOnPartialResponseAndError() {
        // given
        List<Object> tokens = new ArrayList<>();
        tokens.add("Three ");
        tokens.add("Two ");
        tokens.add("One ");
        tokens.add(new RuntimeException("BOOM"));

        StreamingChatModel model = new DummyModel(tokens);

        // when
        List<Object> receivedTokens = new ArrayList<>();
        final Throwable[] thrown = {null};

        model.chat("Create a countdown", onPartialResponseAndError(receivedTokens::add, t -> thrown[0] = t));

        // then
        assertThat(tokens).containsSubsequence(receivedTokens);
        assertThat(thrown[0]).isNotNull();
        assertThat(thrown[0]).isInstanceOf(RuntimeException.class);
        assertThat((thrown[0]).getMessage()).isEqualTo("BOOM");
    }

    static class DummyModel implements StreamingChatModel {

        private final List<Object> stringsAndError;

        public DummyModel(List<Object> stringsAndError) {
            this.stringsAndError = stringsAndError;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            stringsAndError.forEach(obj -> {
                if (obj instanceof String message) {
                    handler.onPartialResponse(message);
                } else if (obj instanceof Throwable problem) {
                    handler.onError(problem);
                }
            });
        }
    }
}
