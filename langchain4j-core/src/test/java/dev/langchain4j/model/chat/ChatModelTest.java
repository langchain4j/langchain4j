package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.CustomChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatModelTest implements WithAssertions {

    public static class UpperCaseEchoModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            List<ChatMessage> messages = chatRequest.messages();
            UserMessage lastMessage = (UserMessage) messages.get(messages.size() - 1);
            return ChatResponse.builder()
                    .aiMessage(new AiMessage(lastMessage.singleText().toUpperCase(Locale.ROOT)))
                    .build();
        }
    }

    @Test
    void generate() {
        ChatModel model = new UpperCaseEchoModel();

        assertThat(model.chat("how are you?")).isEqualTo("HOW ARE YOU?");

        {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage("Hello"));
            messages.add(new AiMessage("Hi"));
            messages.add(new UserMessage("How are you?"));

            ChatResponse response = model.chat(messages);

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }

        {
            ChatResponse response =
                    model.chat(new UserMessage("Hello"), new AiMessage("Hi"), new UserMessage("How are you?"));

            assertThat(response.aiMessage().text()).isEqualTo("HOW ARE YOU?");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void chatAsync_string_should_cancel_the_underlying_request(boolean mayInterruptIfRunning) {
        CompletableFuture<ChatResponse> source = new CompletableFuture<>();
        ChatModel model = asyncModelReturning(source);

        CompletableFuture<String> result = model.chatAsync("hello");

        assertThat(result).isNotDone();
        assertThat(source).isNotDone();

        assertThat(result.cancel(mayInterruptIfRunning)).isTrue();

        assertThat(result).isCancelled();
        assertThat(source).isCancelled();
    }

    @Test
    void chatAsync_string_should_complete_with_the_response_text() {
        CompletableFuture<ChatResponse> source = new CompletableFuture<>();
        ChatModel model = asyncModelReturning(source);

        CompletableFuture<String> result = model.chatAsync("hello");
        assertThat(result).isNotDone();

        source.complete(ChatResponse.builder().aiMessage(AiMessage.from("Hi")).build());

        assertThat(result).isCompletedWithValue("Hi");
        assertThat(result.cancel(true)).isFalse();
        assertThat(source).isNotCancelled();
    }

    @Test
    void chatAsync_string_should_propagate_failure_without_cancelling_the_request() {
        CompletableFuture<ChatResponse> source = new CompletableFuture<>();
        ChatModel model = asyncModelReturning(source);
        RuntimeException failure = new RuntimeException("request failed");

        CompletableFuture<String> result = model.chatAsync("hello");
        source.completeExceptionally(failure);

        assertThatThrownBy(result::get).isInstanceOf(ExecutionException.class).hasCause(failure);
        assertThat(result).isNotCancelled();
        assertThat(source).isNotCancelled();
    }

    private static ChatModel asyncModelReturning(CompletableFuture<ChatResponse> response) {
        return new ChatModel() {
            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
                return response;
            }
        };
    }

    @Test
    void should_not_lose_provider_specific_parameters_when_default_request_parameters_are_not_overridden() {

        // given
        AtomicReference<ChatRequest> receivedRequest = new AtomicReference<>();
        ChatModel model = new ChatModel() {

            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                receivedRequest.set(chatRequest);
                return ChatResponse.builder().aiMessage(new AiMessage("Hi")).build();
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
        model.chat(chatRequest);

        // then
        ChatRequestParameters parameters = receivedRequest.get().parameters();
        assertThat(parameters).isInstanceOf(CustomChatRequestParameters.class);
        assertThat(((CustomChatRequestParameters) parameters).customParameter()).isEqualTo("custom-value");
        assertThat(parameters.temperature()).isEqualTo(0.7);
    }
}
