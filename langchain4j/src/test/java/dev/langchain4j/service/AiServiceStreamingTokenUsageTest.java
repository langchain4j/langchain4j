package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;
import org.junit.jupiter.api.Test;

class AiServiceStreamingTokenUsageTest {

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    interface ReactiveAssistant {

        Flow.Publisher<AiServiceStreamingEvent> chat(String userMessage);
    }

    @Test
    void should_not_fail_when_the_model_reports_no_token_usage() throws Exception {

        Assistant assistant = AiServices.create(Assistant.class, modelReturning(null));

        ChatResponse response = complete(assistant.chat("Hello"));

        assertThat(response.metadata()).isInstanceOf(TestChatResponseMetadata.class);
        assertThatCode(response::tokenUsage).doesNotThrowAnyException();
        assertThat(response.tokenUsage()).isNull();
        assertThatCode(response::toString).doesNotThrowAnyException();
    }

    @Test
    void should_keep_the_token_usage_reported_by_the_model() throws Exception {

        TestTokenUsage tokenUsage = new TestTokenUsage(1, 2);

        Assistant assistant = AiServices.create(Assistant.class, modelReturning(tokenUsage));

        ChatResponse response = complete(assistant.chat("Hello"));

        assertThat(response.tokenUsage()).isSameAs(tokenUsage);
    }

    @Test
    void should_not_fail_on_the_reactive_path_when_the_model_reports_no_token_usage() throws Exception {

        ReactiveAssistant assistant = AiServices.create(ReactiveAssistant.class, modelReturning(null));

        ChatResponse response = complete(assistant.chat("Hello"));

        assertThat(response.metadata()).isInstanceOf(TestChatResponseMetadata.class);
        assertThatCode(response::tokenUsage).doesNotThrowAnyException();
        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    void should_keep_the_token_usage_reported_by_the_model_on_the_reactive_path() throws Exception {

        TestTokenUsage tokenUsage = new TestTokenUsage(1, 2);

        ReactiveAssistant assistant = AiServices.create(ReactiveAssistant.class, modelReturning(tokenUsage));

        ChatResponse response = complete(assistant.chat("Hello"));

        assertThat(response.tokenUsage()).isSameAs(tokenUsage);
    }

    private static ChatResponse complete(Flow.Publisher<AiServiceStreamingEvent> publisher) throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AiServiceStreamingEvent event) {
                if (event instanceof AiServiceStreamingEvent.FinalResponseEvent finalResponse) {
                    future.complete(finalResponse.chatResponse());
                }
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }

            @Override
            public void onComplete() {}
        });
        return future.get(10, SECONDS);
    }

    private static ChatResponse complete(TokenStream tokenStream) throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        tokenStream
                .onPartialResponse(partialResponse -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        return future.get(10, SECONDS);
    }

    private static StreamingChatModel modelReturning(TokenUsage tokenUsage) {
        return new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onPartialResponse("Hi");
                handler.onCompleteResponse(chatResponse(tokenUsage));
            }

            @Override
            public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
                TubeConfiguration config = new TubeConfiguration()
                        .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                        .withBufferSize(256);

                return ZeroPublisher.create(config, tube -> {
                    tube.send(new PartialResponse("Hi"));
                    tube.send(new CompleteResponse(chatResponse(tokenUsage)));
                    tube.complete();
                });
            }
        };
    }

    private static ChatResponse chatResponse(TokenUsage tokenUsage) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("Hi"))
                .metadata(TestChatResponseMetadata.builder()
                        .id("id")
                        .modelName("model")
                        .tokenUsage(tokenUsage)
                        .build())
                .build();
    }

    /**
     * Mimics the provider metadata classes that narrow {@link ChatResponseMetadata#tokenUsage()}
     * to their own {@link TokenUsage} subtype.
     */
    static class TestChatResponseMetadata extends ChatResponseMetadata {

        TestChatResponseMetadata(Builder builder) {
            super(builder);
        }

        @Override
        public TestTokenUsage tokenUsage() {
            return (TestTokenUsage) super.tokenUsage();
        }

        @Override
        public Builder toBuilder() {
            return (Builder) super.toBuilder(builder());
        }

        public static Builder builder() {
            return new Builder();
        }

        static class Builder extends ChatResponseMetadata.Builder<Builder> {

            @Override
            public TestChatResponseMetadata build() {
                return new TestChatResponseMetadata(this);
            }
        }
    }

    static class TestTokenUsage extends TokenUsage {

        TestTokenUsage(Integer inputTokenCount, Integer outputTokenCount) {
            super(inputTokenCount, outputTokenCount);
        }
    }
}
