package dev.langchain4j.service.guardrail;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
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
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A retry or reprompt must resend the original conversation (system message, user message, the rejected answer)
 * whether or not the AI Service has a chat memory. Without one, the guardrail has no memory to read the
 * conversation from, so it must come from the request that produced the rejected answer.
 */
class OutputGuardrailWithoutChatMemoryTest {

    private static final String SYSTEM = "You summarise insurance claims";
    private static final String USER = "Summarise: my car was hit in the parking lot";
    private static final String REJECTED = "BAD a far too long summary";
    private static final String REPROMPT = "Please answer in one sentence";

    interface SyncAssistant {
        @dev.langchain4j.service.SystemMessage(SYSTEM)
        String chat(String message);
    }

    interface AsyncAssistant {
        @dev.langchain4j.service.SystemMessage(SYSTEM)
        CompletableFuture<String> chat(String message);
    }

    interface TokenStreamAssistant {
        @dev.langchain4j.service.SystemMessage(SYSTEM)
        TokenStream chat(String message);
    }

    interface PublisherAssistant {
        @dev.langchain4j.service.SystemMessage(SYSTEM)
        Flow.Publisher<String> chat(String message);
    }

    enum Mode {
        SYNC,
        ASYNC,
        TOKEN_STREAM,
        PUBLISHER
    }

    enum Outcome {
        REPROMPT,
        RETRY
    }

    static Stream<Arguments> modesAndOutcomes() {
        return Stream.of(Mode.values())
                .flatMap(mode -> Stream.of(Outcome.values()).map(outcome -> Arguments.of(mode, outcome)));
    }

    @ParameterizedTest(name = "{0} {1}, no chat memory")
    @MethodSource("modesAndOutcomes")
    void without_chat_memory_the_second_request_carries_the_original_conversation(Mode mode, Outcome outcome)
            throws Exception {
        var requests = run(mode, outcome, false);

        assertThat(requests).hasSize(2);
        assertThat(texts(requests.get(1))).containsExactlyElementsOf(expectedSecondRequest(outcome));
    }

    @ParameterizedTest(name = "{0} {1}, with chat memory")
    @MethodSource("modesAndOutcomes")
    void with_chat_memory_the_second_request_is_unchanged(Mode mode, Outcome outcome) throws Exception {
        var requests = run(mode, outcome, true);

        assertThat(requests).hasSize(2);
        assertThat(texts(requests.get(1))).containsExactlyElementsOf(expectedSecondRequest(outcome));
    }

    @Test
    void without_chat_memory_a_reprompt_after_a_tool_call_carries_the_tool_round_trip() {
        var toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("lookup")
                .arguments("{}")
                .build());
        var model = ChatModelMock.thatAlwaysResponds(toolCall, AiMessage.from(REJECTED), AiMessage.from("good"));
        var tools = new Object() {
            @Tool("looks something up")
            public String lookup() {
                return "looked up";
            }
        };

        var assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(model)
                .tools(tools)
                .outputGuardrails(new RejectFirstAnswer(Outcome.REPROMPT))
                .build();

        assertThat(assistant.chat(USER)).isEqualTo("good");
        assertThat(model.requests()).hasSize(3);
        assertThat(texts(model.requests().get(2)))
                .containsExactly(
                        "SYSTEM:" + SYSTEM,
                        "USER:" + USER,
                        "AI:tool lookup",
                        "TOOL_EXECUTION_RESULT:looked up",
                        "AI:" + REJECTED,
                        "USER:" + REPROMPT);
    }

    private static List<String> expectedSecondRequest(Outcome outcome) {
        return outcome == Outcome.REPROMPT
                ? List.of("SYSTEM:" + SYSTEM, "USER:" + USER, "AI:" + REJECTED, "USER:" + REPROMPT)
                : List.of("SYSTEM:" + SYSTEM, "USER:" + USER, "AI:" + REJECTED);
    }

    private static List<ChatRequest> run(Mode mode, Outcome outcome, boolean withChatMemory) throws Exception {
        var guardrail = new RejectFirstAnswer(outcome);
        var answers = List.of(AiMessage.from(REJECTED), AiMessage.from("good"));

        return switch (mode) {
            case SYNC -> {
                var model = ChatModelMock.thatAlwaysResponds(answers.toArray(AiMessage[]::new));
                var assistant = build(
                        SyncAssistant.class,
                        withChatMemory,
                        b -> b.chatModel(model).outputGuardrails(guardrail));
                assertThat(assistant.chat(USER)).isEqualTo("good");
                yield model.requests();
            }
            case ASYNC -> {
                var model = ChatModelMock.thatAlwaysResponds(answers.toArray(AiMessage[]::new));
                var assistant = build(
                        AsyncAssistant.class,
                        withChatMemory,
                        b -> b.chatModel(model).outputGuardrails(guardrail));
                assertThat(assistant.chat(USER).get(10, SECONDS)).isEqualTo("good");
                yield model.requests();
            }
            case TOKEN_STREAM -> {
                var model = StreamingChatModelMock.thatAlwaysStreams(answers);
                var assistant = build(
                        TokenStreamAssistant.class,
                        withChatMemory,
                        b -> b.streamingChatModel(model).outputGuardrails(guardrail));
                var future = new CompletableFuture<String>();
                assistant
                        .chat(USER)
                        .onCompleteResponse(
                                response -> future.complete(response.aiMessage().text()))
                        .onError(future::completeExceptionally)
                        .start();
                assertThat(future.get(10, SECONDS)).isEqualTo("good");
                yield model.requests();
            }
            case PUBLISHER -> {
                var model = StreamingChatModelMock.thatAlwaysStreams(answers);
                var assistant = build(
                        PublisherAssistant.class,
                        withChatMemory,
                        b -> b.streamingChatModel(model).outputGuardrails(guardrail));
                assertThat(subscribeAndAwait(assistant.chat(USER))).isEqualTo("good");
                yield model.requests();
            }
        };
    }

    private static <T> T build(
            Class<T> type, boolean withChatMemory, Function<AiServices<T>, AiServices<T>> customizer) {
        var builder = customizer.apply(AiServices.builder(type));
        if (withChatMemory) {
            builder.chatMemory(MessageWindowChatMemory.withMaxMessages(20));
        }
        return builder.build();
    }

    private static List<String> texts(ChatRequest request) {
        return request.messages().stream()
                .map(OutputGuardrailWithoutChatMemoryTest::text)
                .toList();
    }

    private static String text(ChatMessage message) {
        String text;
        if (message instanceof SystemMessage system) {
            text = system.text();
        } else if (message instanceof UserMessage user) {
            text = user.singleText();
        } else if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
            text = "tool " + ai.toolExecutionRequests().get(0).name();
        } else if (message instanceof AiMessage ai) {
            text = ai.text();
        } else if (message instanceof ToolExecutionResultMessage result) {
            text = result.text();
        } else {
            text = message.toString();
        }
        return message.type() + ":" + text;
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

    private static final class RejectFirstAnswer implements OutputGuardrail {
        private final Outcome outcome;

        private RejectFirstAnswer(Outcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            if (!responseFromLLM.text().startsWith("BAD")) {
                return success();
            }
            return outcome == Outcome.REPROMPT ? reprompt("too long", REPROMPT) : retry("try again");
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }
}
