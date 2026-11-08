package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.CustomChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class StreamingChatModelTest implements WithAssertions {

    public static class StreamingUpperCaseEchoModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            List<ChatMessage> messages = chatRequest.messages();
            UserMessage lastMessage = (UserMessage) messages.get(messages.size() - 1);
            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(new AiMessage(lastMessage.singleText().toUpperCase(Locale.ROOT)))
                    .build();
            handler.onCompleteResponse(chatResponse);
        }
    }

    public static final class CollectorResponseHandler implements StreamingChatResponseHandler {

        private final List<ChatResponse> responses = new ArrayList<>();
        private final List<Object> rawEvents = new ArrayList<>();

        public List<ChatResponse> responses() {
            return responses;
        }

        public List<Object> rawEvents() {
            return rawEvents;
        }

        @Override
        public void onPartialResponse(String partialResponse) {}

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            rawEvents.add(rawEvent);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            responses.add(completeResponse);
        }

        @Override
        public void onError(Throwable error) {}
    }

    @Test
    void generate() {
        StreamingChatModel model = new StreamingUpperCaseEchoModel();

        {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello"));
            messages.add(new AiMessage("Hi"));
            messages.add(new UserMessage("How are you?"));

            CollectorResponseHandler handler = new CollectorResponseHandler();
            model.chat(messages, handler);

            ChatResponse response = handler.responses().get(0);

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }

        {
            CollectorResponseHandler handler = new CollectorResponseHandler();
            model.chat("How are you?", handler);

            ChatResponse response = handler.responses().get(0);

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }
    }

    @Test
    void should_forward_raw_event_callback() {
        Object rawEvent = "provider.raw.event";

        StreamingChatModel model = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onUnmappedRawEvent(rawEvent);
                handler.onCompleteResponse(
                        ChatResponse.builder().aiMessage(new AiMessage("done")).build());
            }
        };

        CollectorResponseHandler handler = new CollectorResponseHandler();
        model.chat("search", handler);

        assertThat(handler.rawEvents()).containsExactly(rawEvent);
    }

    @Test
    void should_not_lose_provider_specific_parameters_when_default_request_parameters_are_not_overridden() {

        // given
        AtomicReference<ChatRequest> receivedRequest = new AtomicReference<>();
        StreamingChatModel model = new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                receivedRequest.set(chatRequest);
                handler.onCompleteResponse(
                        ChatResponse.builder().aiMessage(new AiMessage("Hi")).build());
            }
        };

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(new UserMessage("Hello"))
                .parameters(CustomChatRequestParameters.builder()
                        .temperature(0.7)
                        .customParameter("custom-value")
                        .build())
                .build();

        // when
        model.chat(chatRequest, new CollectorResponseHandler());

        // then
        ChatRequestParameters parameters = receivedRequest.get().parameters();
        assertThat(parameters).isInstanceOf(CustomChatRequestParameters.class);
        assertThat(((CustomChatRequestParameters) parameters).customParameter()).isEqualTo("custom-value");
        assertThat(parameters.temperature()).isEqualTo(0.7);
    }
}
