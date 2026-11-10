package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.callToolsThenAnswer;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.test.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.test.service.AiServiceCapture;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * capture(...) works the same way whatever the AI Service method returns.
 */
@Timeout(30)
class CaptureReturnTypesTest {

    PolicyExpert policyExpert = CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("unused"));

    @Test
    void should_capture_ai_service_returning_string() {
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                ChatModelMock.thatResponds(callToolsThenAnswer(
                        "Your booking MS-777 starts on 2026-11-02.",
                        toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"))),
                bookings(),
                policyExpert);

        AiServiceInvocation invocation =
                capture(() -> agent.chat("klaus", "When does booking MS-777 start?")).invocation();

        assertThat(invocation.outputText()).isEqualTo("Your booking MS-777 starts on 2026-11-02.");
        assertThat(invocation.output()).isEqualTo("Your booking MS-777 starts on 2026-11-02.");
        assertThat(invocation.invocationContext().methodName()).isEqualTo("chat");
        assertThat(invocation.invocationContext().invocationId()).isNotNull();
        assertThat(invocation.invocationContext().interfaceName()).isEqualTo(CustomerSupportAgent.class.getName());
        assertThat(invocation.invocationContext().methodArguments())
                .containsExactly("klaus", "When does booking MS-777 start?");
        assertThat(invocation.error()).isNull();
        assertThat(invocation.isCancelled()).isFalse();
        assertThat(invocation)
                .hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-777"))
                .hasNoFailedToolExecutions();
    }

    @Test
    void should_capture_ai_service_returning_result() {
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                ChatModelMock.thatResponds(callToolsThenAnswer(
                        "Booking MS-777 cancelled.", toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"))),
                bookings(),
                policyExpert);

        AiServiceInvocation invocation =
                capture(() -> agent.chatWithDetails("klaus", "Cancel MS-777")).invocation();

        // the content of the Result, not the Result itself
        assertThat(invocation.output()).isEqualTo("Booking MS-777 cancelled.");
        assertThat(invocation.outputText()).isEqualTo("Booking MS-777 cancelled.");
        assertThat(invocation).hasToolExecuted("cancelBooking");
    }

    @Test
    void should_assert_on_result_without_capture() {
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                ChatModelMock.thatResponds(callToolsThenAnswer(
                        "Booking MS-777 cancelled.", toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"))),
                bookings(),
                policyExpert);

        AiServiceCapture result = capture(() -> agent.chatWithDetails("klaus", "Cancel MS-777"));

        assertThat(result)
                .hasToolsExecutedExactlyInAnyOrder("cancelBooking")
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
                .hasNoFailedToolExecutions();
        assertThat(result.outputText()).isEqualTo("Booking MS-777 cancelled.");
    }

    @Test
    void should_capture_token_stream_started_in_the_lambda() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatStreams(
                        toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-888\"}"),
                        AiMessage.from("It ends on 2026-12-27.")),
                bookings());

        AiServiceInvocation invocation = capture(() -> agent.chat("When does MS-888 end?")
                        .ignoreErrors()
                        .start())
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("It ends on 2026-12-27.");
        // for a streaming method, the output is the whole streamed text, and the final ChatResponse is kept apart
        assertThat(invocation.output()).isEqualTo("It ends on 2026-12-27.");
        assertThat(invocation.lastResponse()).isInstanceOf(ChatResponse.class);
        assertThat(invocation).hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", "MS-888"));
    }

    @Test
    void should_wait_for_token_stream_started_by_captured_code() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Hello", ", how can I help?"), bookings());
        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = capture(() -> agent.chat("Hi")
                        .onPartialResponse(tokens::add)
                        .onError(error -> {})
                        .start())
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Hello, how can I help?");
        assertThat(String.join("", tokens)).isEqualTo("Hello, how can I help?");
    }

    @Test
    void should_capture_token_stream_started_with_the_handlers_it_was_given() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Hi there"), bookings());
        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = capture(() -> agent.chat("Hi")
                        .onPartialResponse(tokens::add)
                        .onError(error -> {})
                        .start())
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Hi there");
        assertThat(String.join("", tokens)).isEqualTo("Hi there");
    }

    @Test
    void should_fail_fast_when_the_returned_token_stream_was_not_started() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Hi there"), bookings());
        List<String> tokens = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> capture(() -> agent.chat("Hi").onPartialResponse(tokens::add).ignoreErrors()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was not started")
                .hasMessageContaining("call start() in the lambda");
        assertThat(tokens).isEmpty();
    }

    @Test
    void should_wait_for_returned_completable_future() {
        AsyncCustomerSupportAgent agent = CustomerSupport.asyncCustomerSupportAgent(
                ChatModelMock.thatResponds(callToolsThenAnswer(
                        "Cancelled.", toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"))),
                bookings());
        AtomicReference<CompletableFuture<String>> future = new AtomicReference<>();

        AiServiceInvocation invocation = capture(() -> {
                    future.set(agent.chat("Cancel MS-777"));
                    return future.get();
                })
                .invocation();

        assertThat(future.get()).isCompletedWithValue("Cancelled.");
        assertThat(invocation.outputText()).isEqualTo("Cancelled.");
        assertThat(invocation).hasToolExecuted("cancelBooking");
    }

    @Test
    void should_wait_for_completable_future_that_is_not_returned() {
        AsyncCustomerSupportAgent agent = CustomerSupport.asyncCustomerSupportAgent(
                ChatModelMock.thatAlwaysResponds("Hello!"), bookings());

        AiServiceInvocation invocation = capture(() -> {
                    agent.chat("Hi");
                })
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Hello!");
    }

    @Test
    void should_subscribe_to_returned_publisher_and_wait_for_it() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatStreams(
                        toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"), AiMessage.from("Done.")),
                bookings());

        AiServiceInvocation invocation = capture(() -> agent.chatReactive("Cancel MS-777")).invocation();

        assertThat(invocation.outputText()).isEqualTo("Done.");
        assertThat(invocation).hasToolExecuted("cancelBooking");
    }

    @Test
    void should_wait_for_publisher_subscribed_to_by_captured_code() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Good ", "morning"), bookings());
        List<String> items = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = capture(() -> agent.chatReactive("Hi").subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(String item) {
                        items.add(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {}

                    @Override
                    public void onComplete() {}
                }))
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Good morning");
        assertThat(String.join("", items)).isEqualTo("Good morning");
    }

    @Test
    void should_capture_structured_output() {
        BookingRequestExtractor extractor = CustomerSupport.bookingRequestExtractor(ChatModelMock.thatAlwaysResponds(
                "{\"bookingNumber\":\"MS-777\",\"action\":\"cancel\",\"numberOfGuests\":2}"));

        AiServiceInvocation invocation = capture(() -> extractor.extract(
                        "Hi, please cancel MS-777 for both of us. Klaus"))
                .invocation();

        assertThat(invocation.output()).isEqualTo(new BookingRequest("MS-777", "cancel", 2));
        assertThat(invocation).hasNoToolExecutions();
        assertThatThrownBy(invocation::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service method produced a BookingRequest, not text: use output() instead");
    }
}
