package dev.langchain4j.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ChatExchangeTest {

    static final ChatResponse RESPONSE =
            ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build();

    static class FixedResponseExecutor implements ChatExecutor {

        @Override
        public ChatResponse execute() {
            return RESPONSE;
        }

        @Override
        public ChatResponse execute(List<ChatMessage> chatMessages) {
            return RESPONSE;
        }

        @Override
        public CompletableFuture<ChatResponse> executeAsync(List<ChatMessage> chatMessages) {
            return CompletableFuture.completedFuture(RESPONSE);
        }
    }

    @Test
    void default_exchange_should_not_know_the_request() {

        ChatExecutor executor = new FixedResponseExecutor();
        List<ChatMessage> messages = List.of(UserMessage.from("Hi"));

        assertThat(executor.exchange(messages)).isEqualTo(new ChatExchange(null, RESPONSE));
        assertThat(executor.exchangeAsync(messages).join()).isEqualTo(new ChatExchange(null, RESPONSE));
    }

    @Test
    void output_guardrail_request_with_new_text_should_keep_the_chat_request() {

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("Hi")).build();
        OutputGuardrailRequest request = OutputGuardrailRequest.builder()
                .responseFromLLM(RESPONSE)
                .chatExecutor(new FixedResponseExecutor())
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .build())
                .chatRequest(chatRequest)
                .build();

        assertThat(request.withText("Bye").chatRequest()).isSameAs(chatRequest);
    }

    @Test
    void built_in_exchange_should_return_the_request_with_the_parameters_of_the_original_request() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("Hello");
        ChatRequest original = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .temperature(0.3)
                .build();
        ChatExecutor executor = builtInExecutor(chatModel, original);
        List<ChatMessage> messages = List.of(UserMessage.from("Hi"), UserMessage.from("Again"));

        ChatRequest expected = original.toBuilder().messages(messages).build();
        assertThat(executor.exchange(messages).request()).isEqualTo(expected);
        assertThat(executor.exchangeAsync(messages).join().request()).isEqualTo(expected);
        assertThat(chatModel.requests()).containsExactly(expected, expected);
    }

    @Test
    void exchangeAsync_should_return_failed_future_instead_of_throwing() {

        ChatExecutor builtIn = builtInExecutor(
                ChatModelMock.thatAlwaysResponds("Hello"),
                ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        ChatExecutor throwing = new FixedResponseExecutor() {
            @Override
            public CompletableFuture<ChatResponse> executeAsync(List<ChatMessage> chatMessages) {
                throw new IllegalStateException("failed before the call");
            }
        };

        assertThat(throwing.exchangeAsync(List.of(UserMessage.from("Hi")))).failsWithin(Duration.ofSeconds(1));
        assertThat(builtIn.exchangeAsync(List.of())).failsWithin(Duration.ofSeconds(1));
    }

    @Test
    void cancelling_exchangeAsync_should_cancel_the_model_call() {

        CompletableFuture<ChatResponse> modelCall = new CompletableFuture<>();
        ChatExecutor executor = new FixedResponseExecutor() {
            @Override
            public CompletableFuture<ChatResponse> executeAsync(List<ChatMessage> chatMessages) {
                return modelCall;
            }
        };

        executor.exchangeAsync(List.of(UserMessage.from("Hi"))).cancel(true);

        assertThat(modelCall).isCancelled();
    }

    private static ChatExecutor builtInExecutor(ChatModelMock chatModel, ChatRequest chatRequest) {
        return ChatExecutor.builder(chatModel)
                .chatRequest(chatRequest)
                .invocationContext(InvocationContext.builder()
                        .interfaceName("SomeInterface")
                        .methodName("someMethod")
                        .build())
                .build();
    }
}
