package dev.langchain4j.guardrail;

import dev.langchain4j.model.chat.ChatExchange;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.listener.AiServiceRequestIssuedListener;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class ChatExecutorExchangeTest {

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

    @Test
    void built_in_methods_taking_messages_should_fire_one_response_received_event_per_request() {

        List<BiFunction<ChatExecutor, List<ChatMessage>, ChatResponse>> methods = List.of(
                (executor, messages) -> executor.exchange(messages).response(),
                (executor, messages) -> executor.exchangeAsync(messages).join().response(),
                ChatExecutor::execute,
                (executor, messages) -> executor.executeAsync(messages).join());

        for (BiFunction<ChatExecutor, List<ChatMessage>, ChatResponse> method : methods) {
            List<AiServiceResponseReceivedEvent> events = new CopyOnWriteArrayList<>();
            AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance();
            registrar.register((AiServiceResponseReceivedListener) events::add);
            ChatRequest original =
                    ChatRequest.builder().messages(UserMessage.from("Hi")).build();
            ChatExecutor executor = builtInExecutor(ChatModelMock.thatAlwaysResponds("Hello"), original, registrar);
            List<ChatMessage> messages = List.of(UserMessage.from("Hi"), UserMessage.from("Again"));

            ChatResponse response = method.apply(executor, messages);

            assertThat(events).hasSize(1);
            assertThat(events.get(0).request())
                    .isEqualTo(original.toBuilder().messages(messages).build());
            assertThat(events.get(0).response()).isEqualTo(response);
        }
    }

    @Test
    void built_in_methods_without_messages_should_leave_the_response_received_event_to_the_caller() {

        List<AiServiceRequestIssuedEvent> requests = new CopyOnWriteArrayList<>();
        List<AiServiceResponseReceivedEvent> responses = new CopyOnWriteArrayList<>();
        AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance();
        registrar.register((AiServiceRequestIssuedListener) requests::add);
        registrar.register((AiServiceResponseReceivedListener) responses::add);
        ChatExecutor executor = builtInExecutor(
                ChatModelMock.thatAlwaysResponds("Hello"),
                ChatRequest.builder().messages(UserMessage.from("Hi")).build(),
                registrar);

        executor.execute();
        executor.executeAsync().join();

        assertThat(requests).hasSize(2);
        assertThat(responses).isEmpty();
    }

    @Test
    void executeAsync_should_return_failed_future_instead_of_throwing() {

        AiServiceListenerRegistrar throwingRegistrar = AiServiceListenerRegistrar.newInstance(true);
        throwingRegistrar.register((AiServiceRequestIssuedListener) event -> {
            throw new IllegalStateException("listener failed");
        });
        ChatExecutor executor = builtInExecutor(
                ChatModelMock.thatAlwaysResponds("Hello"),
                ChatRequest.builder().messages(UserMessage.from("Hi")).build(),
                throwingRegistrar);
        ChatExecutor builtIn = builtInExecutor(
                ChatModelMock.thatAlwaysResponds("Hello"),
                ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        assertThat(executor.executeAsync()).failsWithin(Duration.ofSeconds(1));
        assertThat(executor.executeAsync(List.of(UserMessage.from("Hi")))).failsWithin(Duration.ofSeconds(1));
        assertThat(builtIn.executeAsync(List.of())).failsWithin(Duration.ofSeconds(1));
    }

    private static ChatExecutor builtInExecutor(ChatModelMock chatModel, ChatRequest chatRequest) {
        return builtInExecutor(chatModel, chatRequest, null);
    }

    private static ChatExecutor builtInExecutor(
            ChatModelMock chatModel, ChatRequest chatRequest, AiServiceListenerRegistrar registrar) {
        return ChatExecutor.builder(chatModel)
                .chatRequest(chatRequest)
                .invocationContext(InvocationContext.builder()
                        .interfaceName("SomeInterface")
                        .methodName("someMethod")
                        .build())
                .eventListenerRegistrar(registrar)
                .build();
    }
}
