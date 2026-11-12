package dev.langchain4j.service.guardrail;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Output guardrails receive the request that produced the response they check: the last request of the tool loop, and
 * after a reprompt the request of the reprompted call, with the tool round trips it made.
 */
class OutputGuardrailChatRequestTest {

    interface SyncAssistant {
        String chat(String message);
    }

    interface AsyncAssistant {
        CompletableFuture<String> chat(String message);
    }

    interface TokenStreamAssistant {
        TokenStream chat(String message);
    }

    interface PublisherAssistant {
        Flow.Publisher<String> chat(String message);
    }

    enum Mode {
        SYNC,
        ASYNC,
        TOKEN_STREAM,
        PUBLISHER
    }

    static final AiMessage TOOL_CALL = AiMessage.from(ToolExecutionRequest.builder()
            .id("call-1")
            .name("lookup")
            .arguments("{}")
            .build());

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_receive_the_last_request_of_the_tool_loop(Mode mode) throws Exception {

        RecordingGuardrail guardrail = new RecordingGuardrail();

        List<ChatRequest> modelRequests = chat(mode, guardrail, TOOL_CALL, AiMessage.from("good"));

        assertThat(modelRequests).hasSize(2);
        assertThat(guardrail.chatRequests).containsExactly(modelRequests.get(1));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_receive_the_request_of_the_reprompted_call_with_its_tool_round_trip(Mode mode) throws Exception {

        RecordingGuardrail guardrail = new RecordingGuardrail();

        List<ChatRequest> modelRequests =
                chat(mode, guardrail, AiMessage.from("BAD answer"), TOOL_CALL, AiMessage.from("good"));

        assertThat(modelRequests).hasSize(3);
        assertThat(guardrail.chatRequests).containsExactly(modelRequests.get(0), modelRequests.get(2));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_receive_the_requests_of_calls_without_tool_round_trips(Mode mode) throws Exception {

        RecordingGuardrail guardrail = new RecordingGuardrail();

        List<ChatRequest> modelRequests = chat(mode, guardrail, AiMessage.from("BAD answer"), AiMessage.from("good"));

        assertThat(modelRequests).hasSize(2);
        assertThat(guardrail.chatRequests).containsExactly(modelRequests.get(0), modelRequests.get(1));
    }

    private static List<ChatRequest> chat(Mode mode, OutputGuardrail guardrail, AiMessage... answers)
            throws Exception {
        return switch (mode) {
            case SYNC -> {
                var model = ChatModelMock.thatAlwaysResponds(answers);
                assertThat(AiServices.builder(SyncAssistant.class)
                                .chatModel(model)
                                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                                .tools(new Tools())
                                .outputGuardrails(guardrail)
                                .build()
                                .chat("Look it up"))
                        .isEqualTo("good");
                yield model.requests();
            }
            case ASYNC -> {
                var model = ChatModelMock.thatAlwaysResponds(answers);
                assertThat(AiServices.builder(AsyncAssistant.class)
                                .chatModel(model)
                                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                                .tools(new Tools())
                                .outputGuardrails(guardrail)
                                .build()
                                .chat("Look it up")
                                .get(10, SECONDS))
                        .isEqualTo("good");
                yield model.requests();
            }
            case TOKEN_STREAM -> {
                var model = StreamingChatModelMock.thatAlwaysStreams(answers);
                var future = new CompletableFuture<String>();
                AiServices.builder(TokenStreamAssistant.class)
                        .streamingChatModel(model)
                        .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                        .tools(new Tools())
                        .outputGuardrails(guardrail)
                        .build()
                        .chat("Look it up")
                        .onCompleteResponse(response -> future.complete(response.aiMessage().text()))
                        .onError(future::completeExceptionally)
                        .start();
                assertThat(future.get(10, SECONDS)).isEqualTo("good");
                yield model.requests();
            }
            case PUBLISHER -> {
                var model = StreamingChatModelMock.thatAlwaysStreams(answers);
                assertThat(subscribeAndAwait(AiServices.builder(PublisherAssistant.class)
                                .streamingChatModel(model)
                                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                                .tools(new Tools())
                                .outputGuardrails(guardrail)
                                .build()
                                .chat("Look it up")))
                        .isEqualTo("good");
                yield model.requests();
            }
        };
    }

    public static class Tools {

        @Tool("looks something up")
        public String lookup() {
            return "looked up";
        }
    }

    private static final class RecordingGuardrail implements OutputGuardrail {

        final List<ChatRequest> chatRequests = new CopyOnWriteArrayList<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            chatRequests.add(request.chatRequest());
            return request.responseFromLLM().aiMessage().text().startsWith("BAD")
                    ? reprompt("bad", "Answer better")
                    : success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    private static String subscribeAndAwait(Flow.Publisher<String> publisher) throws InterruptedException {
        List<String> items = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                items.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });
        assertThat(latch.await(10, SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        return String.join("", items);
    }
}
