package dev.langchain4j.test.service;

import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.test.mock.EmbeddingModelMock;
import dev.langchain4j.test.mock.ScoringModelMock;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.test.mock.StreamingChatModelMock;
import dev.langchain4j.test.mock.ModerationModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.test.LangChain4jAssertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.reactivestreams.FlowAdapters;
import org.junit.jupiter.api.Nested;

class AiServiceCaptureTest {


    interface Assistant {

        String chat(String message);
    }

    static boolean isOwn(ModelCall call) {
        return call instanceof ChatModelCall chatModelCall && chatModelCall.isOwn();
    }

    interface StreamingAssistant {

        TokenStream chat(String message);
    }

    interface OverloadedAssistant {

        ChatResponse chat(String message);

        TokenStream chat(UserMessage message);

        void tell(String message);

        String tell(UserMessage message);
    }

    interface ModeratedAssistant {

        @Moderate
        String chat(String message);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<String> chat(String message);
    }

    interface EventsAssistant {

        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    interface BookingExtractor {

        Booking extract(String message);
    }

    interface Notifier {

        void notify(String message);
    }

    interface AsyncExtractor {

        CompletableFuture<Booking> extract(String message);
    }

    interface ResultExtractor {

        Result<Booking> extract(String message);
    }

    record Booking(String bookingNumber) {}

    static class BookingTools {

        @Tool
        String getBookingDetails(@P(name = "bookingNumber", value = "the booking number") String bookingNumber) {
            return "Booking " + bookingNumber + " exists";
        }

        @Tool
        String cancelBooking(@P(name = "bookingNumber", value = "the booking number") String bookingNumber) {
            return "Booking " + bookingNumber + " cancelled";
        }
    }

    private static ToolExecutionRequest toolRequest(String toolName) {
        return ToolExecutionRequest.builder()
                .id("1")
                .name(toolName)
                .arguments("{\"bookingNumber\":\"MS-777\"}")
                .build();
    }

    private static Function<ChatRequest, AiMessage> callToolsThenAnswer(String... toolNames) {
        return request -> {
            List<ChatMessage> messages = request.messages();
            long toolResults = messages.stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .count();
            if (toolResults >= toolNames.length) {
                return AiMessage.from("answer");
            }
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id(String.valueOf(toolResults + 1))
                    .name(toolNames[(int) toolResults])
                    .arguments("{\"bookingNumber\":\"MS-777\"}")
                    .build());
        };
    }

    private static final class CancelAfterFirstItem implements Flow.Subscriber<String> {

        private final Flow.Subscriber<? super String> downstream;
        private Flow.Subscription subscription;

        CancelAfterFirstItem(Flow.Subscriber<? super String> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            downstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(String item) {
            downstream.onNext(item);
            subscription.cancel();
            downstream.onComplete();
        }

        @Override
        public void onError(Throwable throwable) {
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }
    }

    static class ExpertTool {

        private final Assistant expert;

        ExpertTool(Assistant expert) {
            this.expert = expert;
        }

        @Tool
        String askExpert(@P(name = "question", value = "the question") String question) {
            return expert.chat(question);
        }
    }

    static class ExtractorTool {

        private final BookingExtractor extractor;

        ExtractorTool(BookingExtractor extractor) {
            this.extractor = extractor;
        }

        @Tool
        String extractBooking(@P(name = "text", value = "the text") String text) {
            return extractor.extract(text).bookingNumber();
        }
    }

    private static Function<ChatRequest, AiMessage> callToolThenAnswer(String toolName, String arguments) {
        return request -> {
            List<ChatMessage> messages = request.messages();
            if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                return AiMessage.from("answer");
            }
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id("1")
                    .name(toolName)
                    .arguments(arguments)
                    .build());
        };
    }

    interface ResultAssistant {

        Result<String> chat(String message);
    }

    static class FailingBookingDetailsTools {

        @Tool
        String cancelBooking(@P("bookingNumber") String bookingNumber) {
            return "Booking " + bookingNumber + " cancelled";
        }

        @Tool
        String getBookingDetails(@P("bookingNumber") String bookingNumber) {
            throw new IllegalStateException("Booking not found");
        }
    }

    private static Function<ChatRequest, AiMessage> callToolThenFail(String toolName) {
        return request -> {
            List<ChatMessage> messages = request.messages();
            if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                throw new RuntimeException("model unavailable");
            }
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id("1")
                    .name(toolName)
                    .arguments("{\"bookingNumber\":\"MS-777\"}")
                    .build());
        };
    }

    private static String lastUserMessage(ChatRequest request) {
        return ((UserMessage) request.messages().get(request.messages().size() - 1))
                .singleText();
    }

    private static void await(CountDownLatch latch) {
        await(latch, 5000);
    }

    private static void await(CountDownLatch latch, long timeoutMillis) {
        try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // the tests of this module do not run in parallel, so a test can change a system property for its duration
    private static <T> T withSystemProperty(String name, String value, Supplier<T> code) {
        String previous = System.getProperty(name);
        System.setProperty(name, value);
        try {
            return code.get();
        } finally {
            if (previous == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, previous);
            }
        }
    }

    @Nested
    class Output {


        // output() and outputText(): what the AI Service produced

        @Test
        void output_should_be_the_structured_output_of_the_ai_service() {
            BookingExtractor extractor = AiServices.builder(BookingExtractor.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("{\"bookingNumber\":\"MS-777\"}"))
                    .build();

            AiServiceCapture capture = capture(() -> extractor.extract("Booking MS-777"));

            Booking booking = capture.output(Booking.class);
            assertThat(booking).isEqualTo(new Booking("MS-777"));
            assertThat(capture.output()).isEqualTo(booking);
            assertThatThrownBy(capture::outputText)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The AI Service method produced a Booking, not text: use output() instead");
        }

        @Test
        void output_should_be_the_content_of_a_result() {
            ResultExtractor extractor = AiServices.builder(ResultExtractor.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("{\"bookingNumber\":\"MS-777\"}"))
                    .build();

            AiServiceCapture capture = capture(() -> extractor.extract("Booking MS-777"));

            assertThat(capture.output(Booking.class)).isEqualTo(new Booking("MS-777"));
            assertThat(capture.lambdaOutput(Result.class).content()).isEqualTo(new Booking("MS-777"));
        }

        @Test
        void output_text_should_be_the_answer_of_a_method_returning_string() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello!"))
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Hi"));

            assertThat(capture.output()).isEqualTo("Hello!");
            assertThat(capture.outputText()).isEqualTo("Hello!");
            assertThat(capture.output(String.class)).isEqualTo("Hello!");
        }

        @Test
        void output_should_be_the_whole_streamed_text_of_a_token_stream() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Hi").ignoreErrors().start());

            assertThat(capture.output()).isEqualTo("Hello");
            assertThat(capture.outputText()).isEqualTo("Hello");
            assertThat(capture.lastResponse().aiMessage().text()).isEqualTo("Hello");
        }

        @Test
        void output_should_be_the_whole_streamed_text_of_a_publisher() {
            ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Hi"));

            assertThat(capture.output()).isEqualTo("Hello");
            assertThat(capture.outputText()).isEqualTo("Hello");
            assertThat(capture.lambdaOutputItems()).isNotEmpty().allSatisfy(item -> assertThat(item).isInstanceOf(String.class));
            assertThat(String.join("", capture.lambdaOutputItems().stream().map(String.class::cast).toList()))
                    .isEqualTo("Hello");
        }

        @Test
        void output_should_be_the_value_of_a_returned_future() {
            AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello!"))
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Hi"));

            assertThat(capture.output()).isEqualTo("Hello!");
            assertThat(capture.outputText()).isEqualTo("Hello!");
            assertThat(capture.lambdaOutput(CompletableFuture.class)).isCompletedWithValue("Hello!");
        }

        @Test
        void output_should_be_the_structured_output_inside_a_future() {
            AsyncExtractor extractor = AiServices.builder(AsyncExtractor.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("{\"bookingNumber\":\"MS-777\"}"))
                    .build();

            AiServiceCapture capture = capture(() -> extractor.extract("Booking MS-777"));

            assertThat(capture.output(Booking.class)).isEqualTo(new Booking("MS-777"));
            assertThatThrownBy(capture::outputText)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The AI Service method produced a Booking, not text: use output() instead");
        }

        @Test
        void output_should_not_depend_on_what_the_lambda_returned() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello!"))
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Hi").length());

            assertThat(capture.output()).isEqualTo("Hello!");
            assertThat(capture.outputText()).isEqualTo("Hello!");
            assertThat(capture.lambdaOutput()).isEqualTo(6);
            assertThat(capture.lambdaOutput(Integer.class)).isEqualTo(6);
            assertThat(capture.lambdaOutput(int.class)).isEqualTo(6);
        }

        @Test
        void output_should_fail_for_a_method_that_returns_nothing() {
            Notifier notifier = AiServices.builder(Notifier.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("ignored"))
                    .build();

            AiServiceCapture capture = capture(() -> notifier.notify("Booking MS-777 was cancelled"));

            assertThatThrownBy(capture::output).hasMessageContaining("returns void, so it produced no output");
            assertThatThrownBy(capture::lambdaOutput).hasMessageStartingWith("The lambda returns nothing");
            assertThatThrownBy(capture::outputText)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The AI Service method returns void, so it produced no text");
            assertThatThrownBy(() -> capture.output(Object.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Notifier.notify -> nothing");
        }

        @Test
        void output_should_throw_when_the_invocation_failed() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            AiServiceCapture capture = captureExpectingFailure(() -> assistant.chat("Hi"));

            assertThatThrownBy(capture::output)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The AI Service invocation failed, so it has no output");
            assertThatThrownBy(capture::outputText)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The AI Service invocation failed, so it has no output");
            assertThatThrownBy(() -> capture.output(String.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("produced a value of type String, but none did")
                    .hasMessageContaining("Assistant.chat -> failed with")
                    .hasMessageContaining("model unavailable");
            LangChain4jAssertions.assertThat(capture).hasFailedWithMessageContaining("model unavailable");
        }

        @Test
        void output_of_type_should_find_the_only_top_level_invocation_with_an_output_of_this_type() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello!"))
                    .build();
            BookingExtractor extractor = AiServices.builder(BookingExtractor.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("{\"bookingNumber\":\"MS-777\"}"))
                    .build();

            AiServiceCapture capture = capture(() -> {
                Booking booking = extractor.extract("Booking MS-777");
                return assistant.chat("Cancel " + booking.bookingNumber());
            });

            assertThat(capture.output(Booking.class)).isEqualTo(new Booking("MS-777"));
            assertThat(capture.output(String.class)).isEqualTo("Hello!");
            assertThat(capture.output(Record.class)).isEqualTo(new Booking("MS-777")); // a subtype matches too
            assertThatThrownBy(capture::output)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected a single top-level AI Service invocation, but 2 were captured")
                    .hasMessageEndingWith("Use output(Class) to get the output of a given type, or invocations().");
            assertThatThrownBy(capture::outputText)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageEndingWith("Use invocations() to choose one.");
            assertThatThrownBy(() -> capture.output(Integer.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage(("Expected a single top-level AI Service invocation that produced a value of type Integer,"
                                    + " but none did. The top-level invocations produced: %s.extract -> Booking, %s.chat ->"
                                    + " String")
                            .formatted(BookingExtractor.class.getName(), Assistant.class.getName()));
        }

        @Test
        void output_of_type_should_fail_when_several_top_level_invocations_produced_this_type() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello!"))
                    .build();

            AiServiceCapture capture = capture(() -> {
                assistant.chat("Hi");
                return assistant.chat("Hi again");
            });

            assertThatThrownBy(() -> capture.output(String.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("produced a value of type String, but 2 did")
                    .hasMessageContaining(Assistant.class.getName() + ".chat -> String, " + Assistant.class.getName()
                            + ".chat -> String");
        }

        @Test
        void output_of_type_should_ignore_nested_invocations() {
            BookingExtractor inner = AiServices.builder(BookingExtractor.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("{\"bookingNumber\":\"MS-777\"}"))
                    .build();
            Assistant outer = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("extractBooking", "{\"text\":\"MS-777\"}")))
                    .tools(new ExtractorTool(inner))
                    .build();

            AiServiceCapture capture = capture(() -> outer.chat("Find MS-777"));

            assertThat(capture.output(String.class)).isEqualTo("answer");
            assertThatThrownBy(() -> capture.output(Booking.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("but none did");
            assertThat(capture.invocation().nestedInvocations().get(0).output()).isEqualTo(new Booking("MS-777"));
        }

        // lambdaOutput() and lambdaOutputItems(): what the lambda returned

        @Test
        void lambda_output_should_fail_for_a_lambda_that_returns_nothing() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello!"))
                    .build();

            AiServiceCapture capture = capture(() -> {
                assistant.chat("Hi");
            });

            assertThatThrownBy(capture::lambdaOutput).hasMessageStartingWith("The lambda returns nothing");
            assertThat(capture.output()).isEqualTo("Hello!");
            assertThatThrownBy(() -> capture.lambdaOutput(String.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("The lambda returns nothing");
            assertThatThrownBy(capture::lambdaOutputItems)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("The lambda returned nothing, not a stream such as a Flux, Multi or Flow.Publisher");
        }

        @Test
        void lambda_output_of_type_should_fail_for_another_type() {
            AiServiceCapture capture = capture(() -> "not an AI Service");

            assertThatThrownBy(() -> capture.lambdaOutput(Integer.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The lambda returned a java.lang.String, not a java.lang.Integer");
        }

        @Test
        void lambda_output_should_throw_when_the_lambda_threw() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            AiServiceCapture capture = captureExpectingFailure(() -> assistant.chat("Hi"));

            assertThatThrownBy(capture::lambdaOutput)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("The lambda threw")
                    .hasMessageEndingWith("so it returned no output")
                    .cause()
                    .hasMessageContaining("model unavailable");
            assertThatThrownBy(capture::lambdaOutputItems)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageEndingWith("so it returned no stream");
        }

        @Test
        void should_capture_code_that_throws_checked_exceptions() throws Exception {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();

            AiServiceInvocation invocation = capture(() -> {
                        if (assistant == null) {
                            throw new IOException("unreachable");
                        }
                        return assistant.chat("Hi");
                    })
                    .invocation();

            assertThat(invocation.outputText()).isEqualTo("answer");
            assertThatThrownBy(() -> capture(() -> {
                        throw new IOException("disk full");
                    }))
                    .isInstanceOf(IOException.class)
                    .hasMessage("disk full");
        }

        @Test
        void should_capture_content_of_result() {
            ResultAssistant assistant = AiServices.builder(ResultAssistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")).invocation();

            assertThat(invocation.outputText()).isEqualTo("answer");
        }
    }


    @Nested
    class Invocations {

        @Test
        void invocation_of_type_should_find_the_only_top_level_invocation_of_an_ai_service() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();
            BookingExtractor extractor = AiServices.builder(BookingExtractor.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("{\"bookingNumber\":\"MS-777\"}"))
                    .build();

            AiServiceCapture capture = capture(() -> {
                assistant.chat("Hi");
                assistant.chat("Hi again");
                return extractor.extract("Booking MS-777");
            });

            assertThat(capture.invocation(BookingExtractor.class).output()).isEqualTo(new Booking("MS-777"));
            assertThat(capture.invocations(Assistant.class)).hasSize(2);
            assertThat(capture.invocations(Notifier.class)).isEmpty();
            assertThatThrownBy(() -> capture.invocation(Assistant.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("Expected a single top-level invocation of " + Assistant.class.getName()
                            + ", but 2 were captured.");
            assertThatThrownBy(() -> capture.invocation(Notifier.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("Expected a single top-level invocation of " + Notifier.class.getName()
                            + ", but there was none.");
        }


        // invocation(), invocations() and invocations()

        @Test
        void invocation_should_fail_when_nothing_was_recorded() {
            AiServiceCapture capture = capture(() -> "not an AI Service");

            assertThat(capture.lambdaOutput()).isEqualTo("not an AI Service");
            assertThat(capture.invocations()).isEmpty();
            assertThat(capture.invocations()).isEmpty();
            assertThatThrownBy(capture::invocation)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("No AI Service invocation was captured.");
            assertThatThrownBy(capture::output)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("No AI Service invocation was captured.");
            assertThatThrownBy(() -> capture.output(String.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("No AI Service invocation was captured.");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("No AI Service invocation was captured.");
        }

        @Test
        void invocation_should_fail_when_several_were_recorded() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(request -> AiMessage.from("answer")))
                    .build();

            AiServiceCapture capture = capture(() -> {
                assistant.chat("first");
                return assistant.chat("second");
            });

            assertThat(capture.invocations()).hasSize(2);
            assertThatThrownBy(capture::invocation)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected a single top-level AI Service invocation, but 2 were captured")
                    .hasMessageContaining(Assistant.class.getName() + ".chat")
                    .hasMessageEndingWith("Use invocations() to choose one.");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasNoToolExecutions())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("but 2 were captured");
            LangChain4jAssertions.assertThat(capture.invocations().get(1)).hasNoToolExecutions();
        }

        @Test
        void invocations_should_be_the_top_level_ones_with_nested_invocations_in_a_tree() {
            Assistant expert = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("expert answer"))
                    .build();
            Assistant outer = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("askExpert", "{\"question\":\"why?\"}")))
                    .tools(new ExpertTool(expert))
                    .build();
            Assistant last = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("last answer"))
                    .build();

            AiServiceCapture capture = capture(() -> {
                outer.chat("first");
                return last.chat("second");
            });

            assertThat(capture.invocations())
                    .extracting(AiServiceInvocation::outputText)
                    .containsExactly("answer", "last answer");
            AiServiceInvocation first = capture.invocations().get(0);
            assertThat(first.nestedInvocations())
                    .extracting(AiServiceInvocation::outputText)
                    .containsExactly("expert answer");
            assertThat(capture.invocations().get(1).nestedInvocations()).isEmpty();
            assertThatThrownBy(() -> capture.output(String.class))
                    .hasMessageContaining("but 2 did"); // the String of the nested invocation is not counted
        }

        @Test
        void request_shortcuts_should_fail_when_several_invocations_were_recorded() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello"))
                    .build();

            AiServiceCapture capture = capture(() -> {
                assistant.chat("Hi");
                return assistant.chat("Hi again");
            });

            assertThatThrownBy(capture::firstRequest).hasMessageContaining("Use invocations() to choose one.");
            assertThatThrownBy(capture::lastRequest).hasMessageContaining("Use invocations() to choose one.");
            assertThatThrownBy(capture::lastResponse).hasMessageContaining("Use invocations() to choose one.");
        }

        @Test
        void should_capture_nothing_after_a_capture_that_failed() {
            Assistant failing = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();
            assertThatThrownBy(() -> capture(() -> failing.chat("Hello"))).hasMessage("model unavailable");

            assistant.chat("not captured");

            assertThat(capture(() -> {}).invocations()).isEmpty();
            assertThat(capture(() -> assistant.chat("recorded")).invocation().invocationContext().parentInvocationId())
                    .isNull();
        }

        @Test
        void should_capture_several_invocations_in_order() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(request -> AiMessage.from("answer " + request.messages().size())))
                    .build();

            AiServiceCapture invocations = capture(() -> {
                assistant.chat("first");
                assistant.chat("second");
            });

            assertThat(invocations.invocations())
                    .extracting(invocation -> invocation.invocationContext().methodArguments().get(0))
                    .containsExactly("first", "second");
            assertThatThrownBy(invocations::invocation)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected a single top-level AI Service invocation, but 2 were captured");
        }

        @Test
        void should_not_capture_invocations_outside_of_captured_code() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();
            assistant.chat("not captured");

            AiServiceCapture invocations = capture(() -> {});

            assertThat(invocations.invocations()).isEmpty();
            assertThatThrownBy(invocations::invocation)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("No AI Service invocation was captured")
                    .hasMessageContaining("on other threads");
        }

        @Test
        void should_keep_recordings_of_parallel_threads_apart() throws Exception {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(request -> AiMessage.from(lastUserMessage(request))))
                    .build();
            ExecutorService executor = Executors.newFixedThreadPool(8);
            try {
                List<Future<AiServiceCapture>> futures = new ArrayList<>();
                for (int i = 0; i < 32; i++) {
                    String message = "message " + i;
                    futures.add(executor.submit(() -> capture(() -> assistant.chat(message))));
                }
                for (int i = 0; i < futures.size(); i++) {
                    assertThat(futures.get(i).get(10, TimeUnit.SECONDS).outputText())
                            .isEqualTo("message " + i);
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }


    @Nested
    class OwnChatModelCallsAndTokens {


        // ownChatModelCalls() and lastResponse()

        @Test
        void should_capture_each_own_chat_model_call_with_its_request_and_response() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("cancelBooking"))
                            .withTokenUsages(new TokenUsage(100, 10), new TokenUsage(150, 20)))
                    .tools(new BookingTools())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Cancel MS-777")).invocation();

            assertThat(invocation.ownChatModelCalls()).hasSize(2);
            ChatModelCall first = invocation.ownChatModelCalls().get(0);
            assertThat(first.request().toolSpecifications())
                    .extracting(ToolSpecification::name)
                    .containsExactlyInAnyOrder("getBookingDetails", "cancelBooking");
            assertThat(first.response().aiMessage().toolExecutionRequests())
                    .extracting(ToolExecutionRequest::name)
                    .containsExactly("cancelBooking");
            assertThat(first.response().tokenUsage()).isEqualTo(new TokenUsage(100, 10));
            ChatModelCall second = invocation.ownChatModelCalls().get(1);
            assertThat(second.request().messages()).last().isInstanceOf(ToolExecutionResultMessage.class);
            assertThat(second.response().aiMessage().text()).isEqualTo("answer");
            assertThat(invocation.lastResponse()).isSameAs(second.response());
            assertThat(invocation.tokenUsage()).isEqualTo(new TokenUsage(250, 30));
            LangChain4jAssertions.assertThat(invocation).hasOwnChatModelCallCount(2).hasOwnChatModelCallCountAtMost(2);
        }

        @Test
        void should_capture_own_chat_model_calls_of_a_token_stream() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatStreams(
                            AiMessage.from(toolRequest("cancelBooking")), AiMessage.from("answer")))
                    .tools(new BookingTools())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Cancel MS-777").ignoreErrors().start()).invocation();

            LangChain4jAssertions.assertThat(invocation).hasOwnChatModelCallCount(2).hasToolExecuted("cancelBooking");
            assertThat(invocation.ownChatModelCalls()).allSatisfy(call -> assertThat(call.request()).isNotNull());
            assertThat(invocation.outputText()).isEqualTo("answer");
        }

        @Test
        void should_report_the_token_usage_of_the_last_own_chat_model_call_as_last_response_of_a_token_stream() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatStreams(
                                    AiMessage.from(toolRequest("cancelBooking")), AiMessage.from("answer"))
                            .withTokenUsages(new TokenUsage(100, 10), new TokenUsage(150, 20)))
                    .tools(new BookingTools())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Cancel MS-777").ignoreErrors().start()).invocation();

            assertThat(invocation.lastResponse().tokenUsage()).isEqualTo(new TokenUsage(150, 20));
            assertThat(invocation.lastResponse()).isSameAs(invocation.ownChatModelCalls().get(1).response());
            assertThat(invocation.tokenUsage()).isEqualTo(new TokenUsage(250, 30));
            assertThat(invocation.outputText()).isEqualTo("answer");
        }

        @Test
        void should_return_the_first_and_last_request_and_the_last_response() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("cancelBooking")))
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Cancel MS-777"));

            AiServiceInvocation invocation = capture.invocation();
            assertThat(invocation.firstRequest()).isSameAs(invocation.ownChatModelCalls().get(0).request());
            assertThat(invocation.lastRequest()).isSameAs(invocation.ownChatModelCalls().get(1).request());
            assertThat(invocation.lastRequest().messages()).last().isInstanceOf(ToolExecutionResultMessage.class);
            assertThat(invocation.lastResponse()).isSameAs(invocation.ownChatModelCalls().get(1).response());
            assertThat(capture.firstRequest()).isSameAs(invocation.firstRequest());
            assertThat(capture.lastRequest()).isSameAs(invocation.lastRequest());
            assertThat(capture.lastResponse()).isSameAs(invocation.lastResponse());
        }

        @Test
        void requests_should_fail_when_the_model_did_not_respond() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            AiServiceCapture capture = captureExpectingFailure(() -> assistant.chat("Hi"));

            assertThatThrownBy(capture::firstRequest)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("The chat model did not respond to the AI Service invocation: it failed with")
                    .hasMessageContaining("model unavailable");
            assertThatThrownBy(capture::lastRequest)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("The chat model did not respond to the AI Service invocation");
            assertThatThrownBy(capture::lastResponse)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("The chat model did not respond to the AI Service invocation: it failed with")
                    .hasMessageContaining("model unavailable");
        }

        @Test
        void own_chat_model_call_count_assertions_should_fail_with_the_actual_count() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("getBookingDetails", "cancelBooking")))
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Cancel MS-777"));

            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasOwnChatModelCallCount(2))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected 2 call(s) of the AI Service to its chat model, but there were 3")
                    .hasMessageContaining("getBookingDetails")
                    .hasMessageContaining("cancelBooking");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasOwnChatModelCallCountAtMost(2))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected at most 2 call(s) of the AI Service to its chat model, but there were 3");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasOwnChatModelCallCountAtMost(0))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected at most 0 call(s) of the AI Service to its chat model, but there were 3");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasOwnChatModelCallCountAtMost(-1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasOwnChatModelCallCount(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_capture_finish_reason_and_final_response_of_a_token_stream() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo")
                            .withFinishReason(FinishReason.LENGTH))
                    .build();

            AiServiceInvocation invocation =
                    capture(() -> assistant.chat("Hi").ignoreErrors().start()).invocation();

            assertThat(invocation.lastResponse().finishReason()).isEqualTo(FinishReason.LENGTH);
            assertThat(invocation.lastResponse().aiMessage().text()).isEqualTo("Hello");
        }

        @Test
        void should_capture_token_usage_and_finish_reason_summed_over_model_calls() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(
                                    callToolThenAnswer("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"))
                            .withTokenUsage(new TokenUsage(100, 10))
                            .withFinishReason(FinishReason.STOP))
                    .tools(new FailingBookingDetailsTools())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Cancel booking MS-777")).invocation();

            assertThat(invocation.tokenUsage()).isEqualTo(new TokenUsage(200, 20));
            LangChain4jAssertions.assertThat(invocation).hasFinishReason(FinishReason.STOP);
        }

        @Test
        void should_count_tokens_and_finish_reason_of_model_call_repeated_by_output_guardrail() {
            AtomicInteger calls = new AtomicInteger();
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(request ->
                                    AiMessage.from(calls.incrementAndGet() == 1 ? "too long answer" : "short"))
                            .withTokenUsages(new TokenUsage(10, 100), new TokenUsage(20, 5)))
                    .outputGuardrails(new OutputGuardrail() {
                        @Override
                        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
                            return responseFromLLM.text().equals("short") ? success() : reprompt("too long", "Be shorter");
                        }
                    })
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")).invocation();

            assertThat(invocation.outputText()).isEqualTo("short");
            assertThat(invocation.tokenUsage().inputTokenCount()).isEqualTo(30);
            assertThat(invocation.tokenUsage().outputTokenCount()).isEqualTo(105);
        }

        @Test
        void should_capture_token_usage_and_finish_reason_of_token_stream() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello")
                            .withTokenUsage(new TokenUsage(10, 2))
                            .withFinishReason(FinishReason.STOP))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi").ignoreErrors().start()).invocation();

            LangChain4jAssertions.assertThat(invocation).hasFinishReason(FinishReason.STOP);
            assertThat(invocation.tokenUsage()).isEqualTo(new TokenUsage(10, 2));
        }

        @Test
        void should_capture_token_usage_and_finish_reason_of_publisher() {
            ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello")
                            .withTokenUsage(new TokenUsage(10, 2))
                            .withFinishReason(FinishReason.STOP))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")).invocation();

            LangChain4jAssertions.assertThat(invocation).hasFinishReason(FinishReason.STOP);
            assertThat(invocation.tokenUsage()).isEqualTo(new TokenUsage(10, 2));
        }

        @Test
        void should_tell_when_the_model_did_not_report_token_usage_or_finish_reason() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")).invocation();

            assertThat(invocation.tokenUsage()).isNull();
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasFinishReason(FinishReason.STOP))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("[Assistant.chat] Expected finish reason <STOP>, but the model did not report it");
        }
    }


    @Nested
    class ToolsAndSources {

        @Test
        void should_capture_what_a_scripted_chat_model_makes_an_ai_service_do() {
            ChatModelMock model = ChatModelMock.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777"))
                    .thenResponds("Booking MS-777 is cancelled.");
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(model)
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Cancel booking MS-777"));

            LangChain4jAssertions.assertThat(capture)
                    .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"));
            assertThat(capture.outputText()).isEqualTo("Booking MS-777 is cancelled.");
            LangChain4jAssertions.assertThat(capture.lastRequest()).hasUserMessageContaining("MS-777");
        }


        // tool assertions

        @Test
        void should_check_how_many_times_a_tool_was_executed() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer("getBookingDetails", "getBookingDetails")))
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Show MS-777 twice"));

            LangChain4jAssertions.assertThat(capture)
                    .hasToolExecutedTimes("getBookingDetails", 2)
                    .hasToolExecutedTimes("cancelBooking", 0);
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasToolExecutedTimes("getBookingDetails", 1))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected tool <getBookingDetails> to be executed successfully 1 time(s), but it was 2 time(s)");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasToolExecutedTimes("getBookingDetails", -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_check_the_order_of_tool_executions() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(
                            callToolsThenAnswer("getBookingDetails", "getBookingDetails", "cancelBooking")))
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Cancel MS-777"));

            LangChain4jAssertions.assertThat(capture)
                    .hasToolsExecutedInOrder("getBookingDetails", "cancelBooking")
                    .hasToolsExecutedInOrder("cancelBooking")
                    .hasToolsExecutedInOrder("getBookingDetails", "getBookingDetails", "cancelBooking")
                    .hasToolsExecutedInOrder();
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture)
                            .hasToolsExecutedInOrder("cancelBooking", "getBookingDetails"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected these tools to be executed successfully in this order: [cancelBooking, getBookingDetails]")
                    .hasMessageContaining("1. getBookingDetails")
                    .hasMessageContaining("3. cancelBooking");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture)
                            .hasToolsExecutedInOrder("getBookingDetails", "cancelBooking", "cancelBooking"))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        void has_no_retrieved_sources_should_check_what_was_retrieved() {
            List<Content> retrieved = new CopyOnWriteArrayList<>();
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .contentRetriever(query -> List.copyOf(retrieved))
                    .build();
            Assistant withoutRetrieval = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();

            LangChain4jAssertions.assertThat(capture(() -> assistant.chat("What is the weather?"))).hasNoRetrievedSources();

            retrieved.add(Content.from("Bookings can be cancelled up to 7 days in advance."));
            AiServiceCapture withSources = capture(() -> assistant.chat("Can I cancel?"));
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(withSources).hasNoRetrievedSources())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected no retrieved sources, but these were retrieved:")
                    .hasMessageContaining("7 days");

            AiServiceCapture notCaptured = capture(() -> withoutRetrieval.chat("Hi"));
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(notCaptured).hasNoRetrievedSources())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("no sources were captured");
        }

        @Test
        void should_capture_tool_executions_of_ai_service_returning_string() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("cancelBooking", "{\"bookingNumber\":\"MS-777\"}")))
                    .tools(new FailingBookingDetailsTools())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Cancel booking MS-777")).invocation();

            LangChain4jAssertions.assertThat(invocation)
                    .hasToolsExecutedExactlyInAnyOrder("cancelBooking")
                    .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                    .hasNoFailedToolExecutions();
            assertThat(invocation.outputText()).isEqualTo("answer");
            assertThat(invocation.invocationContext().methodName()).isEqualTo("chat");
            assertThat(invocation.error()).isNull();
        }

        @Test
        void should_capture_failed_tool_execution() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(
                            callToolThenAnswer("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}")))
                    .tools(new FailingBookingDetailsTools())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Show booking MS-777")).invocation();

            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasNoFailedToolExecutions())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("getBookingDetails({\"bookingNumber\":\"MS-777\"}) -> Booking not found (FAILED)");
        }

        @Test
        void should_capture_retrieved_sources() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .contentRetriever(query -> List.of(Content.from("Bookings can be cancelled up to 7 days in advance.")))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Can I cancel?")).invocation();

            LangChain4jAssertions.assertThat(invocation).hasRetrievedSourceContaining("7 days");
            assertThat(invocation.sources()).hasSize(1);
        }

        @Test
        void should_capture_retrieved_sources_of_publisher() {
            ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("answer"))
                    .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                            .contentRetriever(
                                    query -> List.of(Content.from("Bookings can be cancelled up to 7 days in advance.")))
                            .offloadBlocking(true)
                            .build())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Can I cancel?")).invocation();

            LangChain4jAssertions.assertThat(invocation).hasRetrievedSourceContaining("7 days");
        }

        @Test
        void should_capture_retrieved_sources_of_token_stream() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("answer"))
                    .contentRetriever(query -> List.of(Content.from("Bookings can be cancelled up to 7 days in advance.")))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Can I cancel?").ignoreErrors().start()).invocation();

            LangChain4jAssertions.assertThat(invocation).hasRetrievedSourceContaining("7 days");
        }

        @Test
        void should_tell_that_sources_were_not_captured_when_there_is_no_retrieval() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Can I cancel?")).invocation();

            assertThatThrownBy(invocation::sources)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("has no retrieval augmentor");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasRetrievedSourceContaining("7 days"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("no sources were captured");
        }

        @Test
        void should_tell_when_the_ai_service_does_not_report_whether_tools_failed() {
            AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance();
            InvocationContext context = InvocationContext.builder()
                    .invocationId(UUID.randomUUID())
                    .interfaceName(Assistant.class.getName())
                    .methodName("chat")
                    .timestampNow()
                    .build();

            AiServiceInvocation invocation = capture(() -> {
                        registrar.fireEvent(AiServiceStartedEvent.builder()
                                .invocationContext(context)
                                .userMessage(UserMessage.from("Hello"))
                                .build());
                        registrar.fireEvent(ToolExecutedEvent.builder()
                                .invocationContext(context)
                                .request(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("cancelBooking")
                                        .arguments("{}")
                                        .build())
                                .resultText("cancelled")
                                .build());
                        registrar.fireEvent(AiServiceCompletedEvent.builder()
                                .invocationContext(context)
                                .result("answer")
                                .build());
                    })
                    .invocation();

            LangChain4jAssertions.assertThat(invocation).executedToolNames().containsExactly("cancelBooking");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecuted("cancelBooking"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("to be executed successfully, but the AI Service did not report whether its "
                            + "tool executions failed");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasToolExecutionFailed("cancelBooking"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("did not report whether its tool executions failed");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasNoFailedToolExecutions())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("the AI Service did not report whether its tool executions failed");
        }
    }


    @Nested
    class Streaming {

        @Test
        void should_capture_what_a_scripted_streaming_model_makes_an_ai_service_do() {
            StreamingChatModelMock model = StreamingChatModelMock.thatCallsTool(
                            "cancelBooking", Map.of("bookingNumber", "MS-777"))
                    .thenStreams("Booking ", "cancelled.");
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(model)
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture =
                    capture(() -> assistant.chat("Cancel booking MS-777").ignoreErrors().start());

            LangChain4jAssertions.assertThat(capture)
                    .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"));
            assertThat(capture.outputText()).isEqualTo("Booking cancelled.");
        }

        @Test
        void should_capture_a_stream_that_breaks_after_its_tokens() {
            IllegalStateException failure = new IllegalStateException("connection reset");
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(
                            StreamingChatModelMock.thatStreams("Hel", "lo").thenFailsWith(failure))
                    .build();
            List<String> tokens = new CopyOnWriteArrayList<>();

            AiServiceCapture capture = captureExpectingFailure(() -> assistant
                    .chat("Hi")
                    .onPartialResponse(tokens::add)
                    .ignoreErrors()
                    .start());

            LangChain4jAssertions.assertThat(capture).hasFailedWith(IllegalStateException.class);
            assertThat(tokens).containsExactly("Hel", "lo");
        }

        @Test
        void should_stop_a_streaming_ai_service_with_a_clear_error_when_a_tool_call_is_the_last_step() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(
                            StreamingChatModelMock.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777")))
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = captureExpectingFailure(
                    () -> assistant.chat("Cancel booking MS-777").ignoreErrors().start());

            assertThat(capture.invocation().error()).hasMessageContaining("has no more responses");
        }


        // streaming

        @Test
        void should_capture_a_token_stream_started_in_the_lambda() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatStreams(
                            AiMessage.from(toolRequest("cancelBooking")), AiMessage.from("Ok")))
                    .contentRetriever(query -> List.of(Content.from("Bookings can be cancelled.")))
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Cancel MS-777").ignoreErrors().start());

            assertThat(capture.outputText()).isEqualTo("Ok");
            LangChain4jAssertions.assertThat(capture)
                    .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                    .hasRetrievedSourceContaining("Bookings can be cancelled.")
                    .hasOwnChatModelCallCount(2);
        }

        @Test
        void should_capture_a_token_stream_whose_handlers_belong_to_the_code() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();
            List<String> tokens = new CopyOnWriteArrayList<>();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")
                            .onPartialResponse(tokens::add)
                            .onError(error -> {})
                            .start())
                    .invocation();

            assertThat(tokens).containsExactly("Hel", "lo");
            assertThat(invocation.outputText()).isEqualTo("Hello");
        }

        @Test
        void should_capture_the_strings_of_a_publisher() {
            ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Hi"));

            assertThat(capture.lambdaOutputItems()).containsExactly("Hel", "lo");
            assertThat(capture.outputText()).isEqualTo("Hello");
        }

        @Test
        void should_capture_the_strings_of_a_reactive_streams_publisher() {
            ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();

            AiServiceCapture capture = capture(() -> FlowAdapters.toPublisher(assistant.chat("Hi")));

            assertThat(capture.lambdaOutputItems()).containsExactly("Hel", "lo");
            assertThat(capture.outputText()).isEqualTo("Hello");
        }

        @Test
        void should_capture_the_events_of_a_publisher_of_streaming_events() {
            EventsAssistant assistant = AiServices.builder(EventsAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatStreams(
                            AiMessage.from(toolRequest("cancelBooking")), AiMessage.from("Cancelled")))
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = capture(() -> assistant.chat("Cancel MS-777"));

            assertThat(capture.lambdaOutputItems())
                    .extracting(item -> item.getClass().getSimpleName())
                    .containsSubsequence(
                            "BeforeToolExecutionEvent", "AfterToolExecutionEvent", "PartialResponseEvent", "FinalResponseEvent");
            assertThat(capture.lambdaOutputItems()).last().isInstanceOf(FinalResponseEvent.class);
            assertThat(capture.outputText()).isEqualTo("Cancelled");
            LangChain4jAssertions.assertThat(capture).hasToolExecuted("cancelBooking").hasOwnChatModelCallCount(2);
        }

        @Test
        void should_capture_the_items_streamed_before_cancellation() {
            ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo", "!"))
                    .build();

            AiServiceCapture capture = capture(() -> (Flow.Publisher<String>) subscriber -> assistant.chat("Hi")
                    .subscribe(new CancelAfterFirstItem(subscriber)));

            assertThat(capture.invocation().isCancelled()).isTrue();
            assertThat(capture.lambdaOutputItems()).containsExactly("Hel");
        }

        @Test
        void should_capture_each_of_several_token_streams_in_their_own_invocation() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();

            AiServiceCapture first = capture(() -> assistant.chat("Hi").ignoreErrors().start());
            AiServiceCapture second = capture(() -> assistant.chat("Hi again").ignoreErrors().start());

            assertThat(first.outputText()).isEqualTo("Hello");
            assertThat(second.outputText()).isEqualTo("Hello");
            assertThat(second.invocation().invocationContext().invocationId())
                    .isNotEqualTo(first.invocation().invocationContext().invocationId());
        }

        @Test
        void should_wait_for_token_stream_started_on_another_thread() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                CountDownLatch lambdaEnding = new CountDownLatch(1);
                AiServiceInvocation invocation = capture(() -> {
                            TokenStream tokenStream = assistant.chat("Hi");
                            executor.execute(() -> {
                                // starts the stream only once the lambda is about to return, so capture(...) must wait
                                await(lambdaEnding, 10_000);
                                tokenStream.onPartialResponse(token -> {}).onError(error -> {}).start();
                            });
                            lambdaEnding.countDown();
                        })
                        .invocation();

                assertThat(invocation.outputText()).isEqualTo("Hello");
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void should_capture_cancelled_token_stream() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello"))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant
                            .chat("Hi")
                            .onPartialResponseWithContext(
                                    (partialResponse, context) -> context.streamingHandle().cancel())
                            .onError(error -> {})
                            .start())
                    .invocation();

            assertThat(invocation.isCancelled()).isTrue();
            assertThatThrownBy(invocation::outputText).hasMessage("The AI Service invocation was cancelled, so it has no output");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasNoToolExecutions())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected the invocation to succeed, but it was cancelled");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasFailedWithMessageContaining("x"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("but it did not fail (it was cancelled)");
        }

        @Test
        void should_wait_for_completable_future() {
            CountDownLatch release = new CountDownLatch(1);
            AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                    .chatModel(ChatModelMock.thatResponds(request -> {
                        await(release);
                        return AiMessage.from("answer");
                    }))
                    .build();

            AiServiceInvocation invocation = capture(() -> {
                        CompletableFuture<String> future = assistant.chat("Hi");
                        release.countDown();
                        return future;
                    })
                    .invocation();

            assertThat(invocation.outputText()).isEqualTo("answer");
        }
    }


    @Nested
    class Failures {

        @Test
        void should_stop_an_ai_service_with_a_clear_error_when_a_tool_call_is_the_last_step() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatCallsTool("cancelBooking", Map.of("bookingNumber", "MS-777")))
                    .tools(new BookingTools())
                    .build();

            AiServiceCapture capture = captureExpectingFailure(() -> assistant.chat("Cancel booking MS-777"));

            assertThat(capture.invocation().toolExecutions()).hasSize(1);
            assertThat(capture.invocation().error()).hasMessageContaining("has no more responses");
        }


        // hasSucceeded() and hasNoRetrievedSources()

        @Test
        void has_succeeded_should_pass_for_a_completed_invocation_and_fail_otherwise() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello!"))
                    .build();
            Assistant failing = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            LangChain4jAssertions.assertThat(capture(() -> assistant.chat("Hi"))).hasSucceeded();
            AiServiceCapture failed = captureExpectingFailure(() -> failing.chat("Hi"));
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(failed).hasSucceeded())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected the invocation to succeed, but it failed with:")
                    .hasMessageContaining("model unavailable");
        }

        @Test
        void has_succeeded_should_fail_for_a_cancelled_invocation() {
            ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo", "!"))
                    .build();

            AiServiceCapture capture = capture(() -> (Flow.Publisher<String>) subscriber -> assistant.chat("Hi")
                    .subscribe(new CancelAfterFirstItem(subscriber)));

            assertThat(capture.invocation().isCancelled()).isTrue();
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(capture).hasSucceeded())
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("[ReactiveAssistant.chat] Expected the invocation to succeed, but it was cancelled");
        }

        @Test
        void capture_expecting_failure_should_fail_with_the_exception_of_the_lambda_as_cause_when_nothing_was_captured() {
            IllegalStateException thrown = new IllegalStateException("not an AI Service failure");

            assertThatThrownBy(() -> captureExpectingFailure(() -> {
                        throw thrown;
                    }))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("Expected an AI Service invocation to fail. No AI Service invocation was captured.")
                    .hasMessageContaining("The lambda threw " + thrown)
                    .hasCause(thrown);
        }

        @Test
        void should_check_failure_type() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("outer", new IllegalStateException("root cause"))))
                    .build();

            AiServiceInvocation invocation = captureExpectingFailure(() -> assistant.chat("Hello")).invocation();

            LangChain4jAssertions.assertThat(invocation)
                    .hasFailedWith(IllegalStateException.class)
                    .hasNoToolExecutions();
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                            .hasFailedWith(UnsupportedOperationException.class))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected the invocation to fail with <java.lang.UnsupportedOperationException>");
        }

        @Test
        void should_return_failure_of_token_stream_when_model_throws_from_chat() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysThrows(new RuntimeException("Cannot connect"))
                            .throwingFromChat())
                    .build();

            AiServiceInvocation invocation =
                    captureExpectingFailure(() -> assistant.chat("Hi").ignoreErrors().start()).invocation();

            LangChain4jAssertions.assertThat(invocation).hasFailedWithMessageContaining("Cannot connect");
            assertThatThrownBy(() -> capture(() -> assistant.chat("Hi").ignoreErrors().start()))
                    .hasMessage("Cannot connect");
        }

        @Test
        void should_match_failure_message_of_a_cause() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("outer", new IllegalStateException("root cause"))))
                    .build();

            AiServiceInvocation invocation = captureExpectingFailure(() -> assistant.chat("Hello")).invocation();

            LangChain4jAssertions.assertThat(invocation).hasFailedWithMessageContaining("root cause");
        }

        @Test
        void should_rethrow_error_of_failed_invocation() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            assertThatThrownBy(() -> capture(() -> assistant.chat("Hello"))).hasMessage("model unavailable");
        }

        @Test
        void should_rethrow_error_of_failed_invocation_caught_by_captured_code() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            assertThatThrownBy(() -> capture(() -> {
                        try {
                            assistant.chat("Hello");
                        } catch (RuntimeException ignored) {
                            // a failure caught by the lambda is still a failure of the invocation
                        }
                    }))
                    .hasMessage("model unavailable");
        }

        @Test
        void should_return_failed_invocation_when_failure_is_expected() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolThenFail("getBookingDetails")))
                    .tools(new FailingBookingDetailsTools())
                    .build();

            AiServiceInvocation invocation = captureExpectingFailure(() -> assistant.chat("Hello")).invocation();

            LangChain4jAssertions.assertThat(invocation)
                    .hasFailedWithMessageContaining("model unavailable")
                    .hasToolExecutionFailed("getBookingDetails");
        }

        @Test
        void should_fail_when_failure_is_expected_but_nothing_failed() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();

            assertThatThrownBy(() -> captureExpectingFailure(() -> assistant.chat("Hello")))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("Expected an AI Service invocation to fail. None of the captured invocations failed");
            assertThatThrownBy(() -> captureExpectingFailure(() -> {}))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith(
                            "Expected an AI Service invocation to fail. No AI Service invocation was captured.");
        }

        @Test
        void should_not_rethrow_failure_of_nested_invocation_handled_by_tool() {
            Assistant inner = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("expert unavailable")))
                    .build();
            Assistant outer = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("askExpert", "{\"question\":\"why?\"}")))
                    .tools(new ExpertTool(inner))
                    .build();

            AiServiceInvocation invocation = capture(() -> outer.chat("Hello")).invocation();

            assertThat(invocation.outputText()).isEqualTo("answer");
            LangChain4jAssertions.assertThat(invocation).topLevelOnly().hasToolExecutionFailed("askExpert");
            LangChain4jAssertions.assertThat(invocation.nestedInvocations().get(0))
                    .hasFailedWithMessageContaining("expert unavailable");
        }

        @Test
        void should_fail_assertions_on_failed_invocation_until_failure_is_checked() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            AiServiceInvocation invocation = captureExpectingFailure(() -> assistant.chat("Hello")).invocation();

            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasNoToolExecutions())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected the invocation to succeed, but it failed with:")
                    .hasMessageContaining("model unavailable")
                    .hasMessageContaining("hasFailedWithMessageContaining");
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasModelCallCount(1))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("but it failed with");
            assertThatThrownBy(invocation::outputText)
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The AI Service invocation failed, so it has no output")
                    .cause()
                    .hasMessageContaining("model unavailable");
            LangChain4jAssertions.assertThat(invocation)
                    .hasFailedWithMessageContaining("model unavailable")
                    .hasNoToolExecutions();
        }

        @Test
        void should_fail_assertion_when_invocation_did_not_fail() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hello")).invocation();

            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasFailedWithMessageContaining("x"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("but it did not fail");
        }

        @Test
        void should_rethrow_exception_not_thrown_by_ai_service() {
            assertThatThrownBy(() -> capture(() -> {
                        throw new IllegalArgumentException("bug in the test");
                    }))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("bug in the test");
        }

        @Test
        void should_fail_fast_when_returned_token_stream_was_not_started() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();

            assertThatThrownBy(() -> capture(() -> assistant.chat("Hi").onPartialResponse(token -> {}).ignoreErrors()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("The TokenStream returned by " + StreamingAssistant.class.getName() + ".chat was not started."
                            + " Set the handlers your TokenStream needs and call start() in the lambda, for example:"
                            + " capture(() -> assistant.chat(\"Hi\").ignoreErrors().start()). If start() was called, the"
                            + " framework that implements the AI Service does not report the events of its TokenStream"
                            + " for this invocation.");
        }

        @Test
        void should_know_the_return_type_of_overloaded_methods_with_the_same_number_of_arguments() {
            OverloadedAssistant assistant = AiServices.builder(OverloadedAssistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello"))
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();

            AiServiceCapture streamed =
                    capture(() -> assistant.chat(UserMessage.from("Hi")).ignoreErrors().start());
            AiServiceCapture told = capture(() -> assistant.tell("Hi"));

            assertThat(streamed.invocation().output()).isEqualTo("Hello");
            assertThatThrownBy(() -> told.invocation().outputText())
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("The AI Service method returns void, so it produced no text");
        }

        @Test
        void should_fail_when_token_stream_is_neither_started_nor_returned() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hello"))
                    .build();

            assertThatThrownBy(() -> withSystemProperty(Capturer.STREAM_START_GRACE_PROPERTY, "100", () -> capture(() -> {
                        assistant.chat("Hi");
                    })))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("The TokenStream returned by")
                    .hasMessageContaining("StreamingAssistant.chat was not started");
        }

        @Test
        void should_rethrow_the_configuration_error_of_a_token_stream_without_error_handler() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo"))
                    .build();
            List<String> tokens = new CopyOnWriteArrayList<>();

            assertThatThrownBy(() -> capture(() -> assistant.chat("Hi").onPartialResponse(tokens::add).start()))
                    .isInstanceOf(IllegalConfigurationException.class)
                    .hasMessageContaining("One of [onError, ignoreErrors] must be invoked");
            assertThat(tokens).isEmpty();
        }

        @Test
        void should_rethrow_error_of_failed_token_stream() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysThrows(new RuntimeException("stream failed")))
                    .build();
            List<Throwable> errors = new CopyOnWriteArrayList<>();

            assertThatThrownBy(() -> capture(() -> assistant.chat("Hi").onError(errors::add).start()))
                    .hasMessage("stream failed");
            assertThat(errors).singleElement().satisfies(error -> assertThat(error).hasMessage("stream failed"));

            AiServiceInvocation invocation = captureExpectingFailure(() -> assistant.chat("Hi").ignoreErrors().start())
                    .invocation();
            LangChain4jAssertions.assertThat(invocation).hasFailedWithMessageContaining("stream failed");
        }

        @Test
        void should_rethrow_error_thrown_in_a_mock_right_away() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(request -> {
                        throw new AssertionError("unexpected request");
                    }))
                    .build();

            assertThatThrownBy(() -> capture(() -> assistant.chat("Hello")))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("unexpected request");
        }

        @Test
        void should_rethrow_exception_thrown_after_a_failed_invocation() {
            Assistant failing = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            assertThatThrownBy(() -> capture(() -> {
                        try {
                            failing.chat("Hello");
                        } catch (RuntimeException expected) {
                            // the failure is captured
                        }
                        throw new IllegalArgumentException("bug in the test");
                    }))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("bug in the test");
        }

        @Test
        void should_rethrow_failure_of_returned_completable_future() {
            AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .build();

            assertThatThrownBy(() -> capture(() -> assistant.chat("Hi").thenApply(answer -> {
                        throw new IllegalStateException("cannot parse " + answer);
                    })))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("cannot parse answer");
        }

        @Test
        void should_rethrow_error_of_failed_async_invocation() {
            AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            assertThatThrownBy(() -> capture(() -> assistant.chat("Hi"))).hasMessage("model unavailable");
            assertThatThrownBy(() -> capture(() -> {
                        assistant.chat("Hi");
                    }))
                    .hasMessage("model unavailable");

            AiServiceInvocation invocation = captureExpectingFailure(() -> assistant.chat("Hi")).invocation();

            LangChain4jAssertions.assertThat(invocation).hasFailedWithMessageContaining("model unavailable");
        }

        @Test
        void should_rethrow_error_of_failed_reactive_invocation() {
            ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysThrows(new RuntimeException("stream failed")))
                    .build();

            assertThatThrownBy(() -> capture(() -> assistant.chat("Hi"))).hasMessage("stream failed");

            AiServiceInvocation invocation = captureExpectingFailure(() -> assistant.chat("Hi")).invocation();

            LangChain4jAssertions.assertThat(invocation).hasFailedWithMessageContaining("stream failed");
        }
    }


    @Nested
    class EndOfInvocations {


        @Test
        void should_fail_when_returned_value_ended_but_invocation_did_not_report_its_end() {
            // simulates a framework that reports the start of an invocation, but not its end
            assertThatThrownBy(() -> withSystemProperty(Capturer.END_REPORT_GRACE_PROPERTY, "200", () -> capture(() -> {
                        AiServiceListenerRegistrar.newInstance()
                                .fireEvent(AiServiceStartedEvent.builder()
                                        .invocationContext(InvocationContext.builder()
                                                .invocationId(UUID.randomUUID())
                                                .interfaceName("com.example.Assistant")
                                                .methodName("chat")
                                                .timestampNow()
                                                .build())
                                        .userMessage(UserMessage.from("Hi"))
                                        .build());
                        return CompletableFuture.failedFuture(new IllegalStateException("stream cancelled"));
                    })))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(
                            "the AI Service invocation com.example.Assistant.chat (id")
                    .hasMessageContaining("did not report its end")
                    .hasRootCauseMessage("stream cancelled");
        }

        @Test
        @Timeout(10)
        void should_fail_when_lambda_returned_but_blocking_invocation_did_not_report_its_end() {
            // simulates a framework that reports the start of a blocking invocation, but not its end
            assertThatThrownBy(() -> withSystemProperty(Capturer.END_REPORT_GRACE_PROPERTY, "200", () -> capture(() -> {
                        AiServiceListenerRegistrar.newInstance()
                                .fireEvent(AiServiceStartedEvent.builder()
                                        .invocationContext(InvocationContext.builder()
                                                .invocationId(UUID.randomUUID())
                                                .interfaceName(Assistant.class.getName())
                                                .methodName("chat")
                                                .methodArguments(List.of("Hi"))
                                                .timestampNow()
                                                .build())
                                        .userMessage(UserMessage.from("Hi"))
                                        .build());
                        return "answer";
                    })))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("The lambda has returned, and the AI Service method returns only when it is done")
                    .hasMessageContaining("did not report its end");
        }

        @Test
        void should_fail_with_the_unfinished_invocations_when_the_timeout_is_reached() {
            // simulates an asynchronous invocation that never ends
            CompletableFuture<String> neverCompleted = new CompletableFuture<>();
            UUID invocationId = UUID.randomUUID();

            assertThatThrownBy(() -> withSystemProperty(Capturer.TIMEOUT_PROPERTY, "300", () -> capture(() -> {
                        AiServiceListenerRegistrar.newInstance()
                                .fireEvent(AiServiceStartedEvent.builder()
                                        .invocationContext(InvocationContext.builder()
                                                .invocationId(invocationId)
                                                .interfaceName(AsyncAssistant.class.getName())
                                                .methodName("chat")
                                                .methodArguments(List.of("Hi"))
                                                .timestampNow()
                                                .build())
                                        .userMessage(UserMessage.from("Hi"))
                                        .build());
                        return neverCompleted;
                    })))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("capture(...) timed out after 300 ms. These AI Service invocations have not finished: "
                            + AsyncAssistant.class.getName() + ".chat (id " + invocationId
                            + ", last event: AiServiceStartedEvent). Change the timeout with the JVM system property"
                            + " langchain4j.test.capture.timeout-millis (in milliseconds, 0 for no timeout).");
            assertThat(neverCompleted).isCancelled();
        }

        @Test
        void should_time_out_when_the_value_returned_by_the_lambda_never_ends() {
            CompletableFuture<String> neverCompleted = new CompletableFuture<>();

            assertThatThrownBy(() ->
                            withSystemProperty(Capturer.TIMEOUT_PROPERTY, "300", () -> capture(() -> neverCompleted)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith(
                            "capture(...) timed out after 300 ms. The value returned by the lambda has not ended.");
            assertThat(neverCompleted).isCancelled();
        }

        @Test
        void should_time_out_when_an_invocation_was_cancelled_and_the_value_returned_by_the_lambda_never_ends() {
            CompletableFuture<String> neverCompleted = new CompletableFuture<>();
            AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance();
            InvocationContext context = InvocationContext.builder()
                    .invocationId(UUID.randomUUID())
                    .interfaceName(AsyncAssistant.class.getName())
                    .methodName("chat")
                    .methodArguments(List.of("Hi"))
                    .timestampNow()
                    .build();

            assertThatThrownBy(() -> withSystemProperty(Capturer.TIMEOUT_PROPERTY, "300", () -> capture(() -> {
                        registrar.fireEvent(AiServiceStartedEvent.builder()
                                .invocationContext(context)
                                .userMessage(UserMessage.from("Hi"))
                                .build());
                        registrar.fireEvent(AiServiceCancelledEvent.builder()
                                .invocationContext(context)
                                .build());
                        return neverCompleted;
                    })))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith(
                            "capture(...) timed out after 300 ms. The value returned by the lambda has not ended.");
            assertThat(neverCompleted).isCancelled();
        }

        @Test
        void should_not_time_out_when_the_timeout_is_zero() {
            CountDownLatch release = new CountDownLatch(1);
            AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                    .chatModel(ChatModelMock.thatResponds(request -> {
                        await(release, 300);
                        return AiMessage.from("answer");
                    }))
                    .build();

            AiServiceCapture capture =
                    withSystemProperty(Capturer.TIMEOUT_PROPERTY, "0", () -> capture(() -> assistant.chat("Hi")));

            assertThat(capture.outputText()).isEqualTo("answer");
        }

        @Test
        void should_reject_invalid_timeout_property() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello"))
                    .build();

            assertThatThrownBy(() -> withSystemProperty(
                            Capturer.TIMEOUT_PROPERTY, "-1", () -> capture(() -> assistant.chat("Hi"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("The system property langchain4j.test.capture.timeout-millis must be a number of"
                            + " milliseconds (0 or more), but is '-1'");
        }

        @Test
        void should_reject_invalid_grace_period_property() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("Hello"))
                    .build();

            assertThatThrownBy(() -> withSystemProperty(
                            Capturer.STREAM_START_GRACE_PROPERTY,
                            "two seconds",
                            () -> capture(() -> assistant.chat("Hi"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("The system property langchain4j.test.capture.stream-start-grace-millis must be a number of"
                            + " milliseconds (0 or more), but is 'two seconds'");
        }
    }

    @Nested
    class ModelCalls {

        @Test
        void should_capture_the_requests_the_chat_model_received_with_its_default_parameters() {
            ChatModelMock chatModel = new ChatModelMock("answer") {

                @Override
                public ChatRequestParameters defaultRequestParameters() {
                    return ChatRequestParameters.builder()
                            .modelName("mock-model")
                            .temperature(0.0)
                            .build();
                }
            };
            Assistant assistant = AiServices.builder(Assistant.class).chatModel(chatModel).build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")).invocation();

            assertThat(invocation.firstRequest().parameters().modelName()).isEqualTo("mock-model");
            assertThat(invocation.lastRequest().parameters().temperature()).isEqualTo(0.0);
            assertThat(invocation.ownChatModelCalls())
                    .singleElement()
                    .satisfies(call -> assertThat(call.request().parameters().modelName())
                            .isEqualTo("mock-model"));
        }

        @Test
        void should_explain_that_a_chat_model_that_does_not_notify_its_listeners_is_not_captured() {
            ChatModelMock chatModel = new ChatModelMock("answer") {

                @Override
                public ChatResponse chat(ChatRequest chatRequest) {
                    return doChat(chatRequest); // bypasses the listeners, like a model that overrides chat(...)
                }
            };
            Assistant assistant = AiServices.builder(Assistant.class).chatModel(chatModel).build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")).invocation();

            assertThat(invocation.outputText()).isEqualTo("answer");
            assertThat(invocation.ownChatModelCalls()).isEmpty();
            assertThat(invocation.tokenUsage()).isNull();
            String explanation = "The AI Service sent 1 request(s) to its chat model, but the chat model did not notify"
                    + " its listeners, so its calls were not captured. This happens when a chat model overrides"
                    + " chat(...) instead of implementing doChat(...).";
            assertThatThrownBy(invocation::firstRequest).isInstanceOf(AssertionError.class).hasMessage(explanation);
            assertThatThrownBy(invocation::lastResponse).isInstanceOf(AssertionError.class).hasMessage(explanation);
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation).hasOwnChatModelCallCount(1))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining(explanation);
        }

        @Test
        void should_report_the_finish_reason_of_a_streaming_method() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("Hel", "lo")
                            .withFinishReason(FinishReason.STOP))
                    .build();

            AiServiceInvocation invocation =
                    capture(() -> assistant.chat("Hi").ignoreErrors().start()).invocation();

            assertThat(invocation.finishReason()).isEqualTo(FinishReason.STOP);
            assertThat(invocation.finishReason()).isEqualTo(invocation.lastResponse().finishReason());
        }

        @Test
        void should_capture_the_own_chat_calls_of_the_ai_service() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(
                            callToolThenAnswer("cancelBooking", "{\"bookingNumber\":\"MS-777\"}")))
                    .tools(new BookingTools())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Cancel MS-777")).invocation();

            assertThat(invocation.modelCalls())
                    .extracting(Object::getClass, AiServiceCaptureTest::isOwn, ModelCall::hasFailed)
                    .containsExactly(tuple(ChatModelCall.class, true, false), tuple(ChatModelCall.class, true, false));
            assertThat(invocation.modelCalls().get(1).response()).isSameAs(invocation.lastResponse());
            LangChain4jAssertions.assertThat(invocation).hasModelCallCount(2).hasModelCallCount(ChatModelCall.class, 2);
        }

        @Test
        void should_capture_the_model_calls_of_retrieval() {
            EmbeddingModelMock embeddingModel = EmbeddingModelMock.thatAlwaysEmbeds(1f, 2f, 3f);
            InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
            TextSegment segment = TextSegment.from("Bookings can be cancelled.");
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                            .contentRetriever(EmbeddingStoreContentRetriever.builder()
                                    .embeddingStore(embeddingStore)
                                    .embeddingModel(embeddingModel)
                                    .build())
                            .contentAggregator(new ReRankingContentAggregator(ScoringModelMock.thatScores(text -> 0.9)))
                            .build())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Can I cancel?")).invocation();

            assertThat(invocation.modelCalls())
                    .extracting(Object::getClass, AiServiceCaptureTest::isOwn)
                    .containsExactly(
                            tuple(EmbeddingModelCall.class, false),
                            tuple(ScoringModelCall.class, false),
                            tuple(ChatModelCall.class, true));
            LangChain4jAssertions.assertThat(invocation)
                    .hasModelCallCount(3)
                    .hasModelCallCount(EmbeddingModelCall.class, 1);
            assertThatThrownBy(() -> LangChain4jAssertions.assertThat(invocation)
                            .hasModelCallCount(ScoringModelCall.class, 2))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Expected 2 scoring model call(s), but there were 1");
        }

        @Test
        void should_capture_a_chat_call_made_by_a_component_of_the_ai_service() {
            ChatModelMock rewritingModel = ChatModelMock.thatAlwaysResponds("rewritten question");
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                            .queryTransformer(query -> List.of(Query.from(rewritingModel.chat(query.text()))))
                            .contentRetriever(query -> List.of())
                            .build())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Can I cancel?")).invocation();

            assertThat(invocation.modelCalls())
                    .extracting(Object::getClass, AiServiceCaptureTest::isOwn)
                    .containsExactly(tuple(ChatModelCall.class, false), tuple(ChatModelCall.class, true));
        }

        @Test
        void should_not_include_the_model_calls_of_nested_invocations() {
            Assistant expert = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("expert answer"))
                    .build();
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("askExpert", "{\"question\":\"why?\"}")))
                    .tools(new ExpertTool(expert))
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")).invocation();

            assertThat(invocation.modelCalls()).hasSize(2).allMatch(AiServiceCaptureTest::isOwn);
            assertThat(invocation.nestedInvocations().get(0).modelCalls())
                    .singleElement()
                    .matches(AiServiceCaptureTest::isOwn);
            LangChain4jAssertions.assertThat(invocation).includingNested().hasModelCallCount(3);
        }

        @Test
        void should_capture_a_failed_model_call() {
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysThrows(new RuntimeException("model unavailable")))
                    .build();

            AiServiceInvocation invocation = captureExpectingFailure(() -> assistant.chat("Hi")).invocation();

            assertThat(invocation.modelCalls()).singleElement().satisfies(call -> {
                assertThat(isOwn(call)).isTrue();
                assertThat(call.hasFailed()).isTrue();
                assertThat(call.error()).hasMessage("model unavailable");
                assertThat(call.response()).isNull();
            });
        }

        @Test
        void should_capture_the_streaming_chat_calls_of_the_ai_service() {
            StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                    .streamingChatModel(StreamingChatModelMock.thatStreams(
                            AiMessage.from(toolRequest("cancelBooking")), AiMessage.from("answer")))
                    .tools(new BookingTools())
                    .build();

            AiServiceInvocation invocation =
                    capture(() -> assistant.chat("Cancel MS-777").ignoreErrors().start()).invocation();

            assertThat(invocation.modelCalls())
                    .extracting(Object::getClass, AiServiceCaptureTest::isOwn, ModelCall::hasFailed)
                    .containsExactly(tuple(ChatModelCall.class, true, false), tuple(ChatModelCall.class, true, false));
        }

        @Test
        void should_capture_the_call_of_the_moderation_model() {
            ModeratedAssistant assistant = AiServices.builder(ModeratedAssistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .moderationModel(ModerationModelMock.thatNeverFlags())
                    .build();

            AiServiceInvocation invocation = capture(() -> assistant.chat("Hi")).invocation();

            assertThat(invocation.modelCalls())
                    .extracting(Object::getClass, AiServiceCaptureTest::isOwn)
                    .containsExactlyInAnyOrder(tuple(ModerationModelCall.class, false), tuple(ChatModelCall.class, true));
            LangChain4jAssertions.assertThat(invocation).hasModelCallCount(ModerationModelCall.class, 1);
        }

        @Test
        void should_not_report_a_failed_component_call_as_own() {
            ChatModelMock rewritingModel = ChatModelMock.thatAlwaysThrows(new RuntimeException("rewriting failed"));
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                    .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                            .queryTransformer(query -> List.of(Query.from(rewritingModel.chat(query.text()))))
                            .contentRetriever(query -> List.of())
                            .build())
                    .build();

            AiServiceInvocation invocation =
                    captureExpectingFailure(() -> assistant.chat("Can I cancel?")).invocation();

            assertThat(invocation.modelCalls()).singleElement().satisfies(call -> {
                assertThat(call).isInstanceOf(ChatModelCall.class);
                assertThat(call.hasFailed()).isTrue();
                assertThat(isOwn(call)).isFalse();
            });
        }

        @Test
        void should_not_capture_model_calls_made_outside_of_an_ai_service() {
            ChatModelMock model = ChatModelMock.thatAlwaysResponds("answer");

            assertThatThrownBy(() -> capture(() -> model.chat("Hi")).invocation())
                    .isInstanceOf(AssertionError.class)
                    .hasMessageStartingWith("No AI Service invocation was captured.");
        }
    }
}
