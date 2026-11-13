package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.callToolsThenAnswer;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;
import static java.util.concurrent.TimeUnit.SECONDS;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.test.service.ChatModelCall;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.Result;
import dev.langchain4j.test.judge.DecisionModelJudge;
import dev.langchain4j.test.judge.Judge;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The AiServiceCapture API (output(), outputText(), lambdaOutput(), lambdaOutputItems(), ownChatModelCalls()), the newer invocation assertions and
 * a judge with a custom question, used on the AI Services of the customer support application.
 */
@Timeout(30)
class CaptureApiTest {

    static final String EXTRACTED = "{\"bookingNumber\":\"MS-777\",\"action\":\"cancel\",\"numberOfGuests\":2}";
    static final String EMAIL = "Hi, please cancel MS-777 for both of us. Klaus";

    PolicyExpert policyExpert = CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("unused"));

    CustomerSupportAgent agent(ChatModelMock model) {
        return CustomerSupport.customerSupportAgent(model, bookings(), policyExpert);
    }

    // structured output

    @Test
    void should_give_typed_structured_output() {
        BookingRequestExtractor extractor =
                CustomerSupport.bookingRequestExtractor(ChatModelMock.thatAlwaysResponds(EXTRACTED));

        AiServiceCapture capture = capture(() -> extractor.extract(EMAIL));

        BookingRequest request = capture.output(BookingRequest.class);
        assertThat(request).isEqualTo(new BookingRequest("MS-777", "cancel", 2));
        assertThat(capture.lambdaOutput()).isEqualTo(request);
        assertThat(capture).hasSucceeded().hasNoToolExecutions().hasOwnChatModelCallCount(1);
        assertThatThrownBy(capture::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service method produced a BookingRequest, not text: use output() instead");
    }

    @Test
    void should_give_the_structured_output_inside_a_future() {
        BookingRequestExtractor extractor =
                CustomerSupport.bookingRequestExtractor(ChatModelMock.thatAlwaysResponds(EXTRACTED));

        AiServiceCapture capture = capture(() -> extractor.extractAsync(EMAIL));

        // the output is the value inside the future: no need to wait for it
        assertThat(capture.output(BookingRequest.class)).isEqualTo(new BookingRequest("MS-777", "cancel", 2));
        assertThat(capture.lambdaOutput(CompletableFuture.class)).isDone();
        assertThatThrownBy(capture::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service method produced a BookingRequest, not text: use output() instead");
    }

    @Test
    void should_give_result_with_structured_output() {
        BookingRequestExtractor extractor =
                CustomerSupport.bookingRequestExtractor(ChatModelMock.thatAlwaysResponds(EXTRACTED));

        AiServiceCapture capture = capture(() -> extractor.extractWithDetails(EMAIL));

        // the output is the content of the Result
        assertThat(capture.output(BookingRequest.class)).isEqualTo(new BookingRequest("MS-777", "cancel", 2));
        Result<?> result = capture.lambdaOutput(Result.class);
        assertThat(capture.lastResponse()).isEqualTo(result.finalResponse());
        assertThatThrownBy(capture::outputText).hasMessageContaining("not text: use output() instead");
    }

    // text output

    @Test
    void should_give_output_text_of_string_method() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatAlwaysResponds("Hello Klaus, how can I help?"));

        AiServiceCapture capture = capture(() -> agent.chat("klaus", "Hi"));

        assertThat(capture.output()).isEqualTo("Hello Klaus, how can I help?");
        assertThat(capture.outputText()).isEqualTo("Hello Klaus, how can I help?");
    }

    @Test
    void should_capture_void_method() {
        ChatModelMock model = ChatModelMock.thatResponds(
                callToolsThenAnswer("Done.", toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}")));
        CustomerSupportAgent agent = agent(model);

        AiServiceCapture capture = capture(() -> agent.handleInBackground("klaus", "Cancel MS-777"));

        assertThatThrownBy(capture::output).hasMessageContaining("returns void, so it produced no output");
        assertThatThrownBy(capture::lambdaOutput).hasMessageStartingWith("The lambda returns nothing");
        assertThat(capture)
                .hasSucceeded()
                .hasToolExecutedTimes("cancelBooking", 1)
                .hasOwnChatModelCallCount(2);
        assertThatThrownBy(capture::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessage("The AI Service method returns void, so it produced no text");
    }

    // streaming

    @Test
    void should_capture_token_stream_started_in_the_lambda() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams(
                        toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-888\"}"),
                        AiMessage.from("It ends on 2026-12-27.")),
                bookings());

        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceCapture capture = capture(() -> agent.chat("When does MS-888 end?")
                .onPartialResponse(tokens::add)
                .ignoreErrors()
                .start());

        assertThat(capture.output()).isEqualTo("It ends on 2026-12-27."); // the whole streamed text
        assertThat(String.join("", tokens)).isEqualTo(capture.outputText());
        AiServiceInvocation invocation = capture.invocation();
        assertThat(invocation).hasSucceeded().hasToolExecutedTimes("getBookingDetails", 1).hasOwnChatModelCallCount(2);
    }

    @Test
    void should_capture_token_stream_started_with_complete_handlers() throws Exception {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Hi ", "there"), bookings());
        List<String> tokens = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> completeResponse = new CompletableFuture<>();

        AiServiceCapture capture = capture(() -> agent.chat("Hi")
                .onPartialResponse(tokens::add)
                .onCompleteResponse(completeResponse::complete)
                .onError(error -> {})
                .start());

        assertThat(capture.outputText()).isEqualTo("Hi there");
        assertThat(String.join("", tokens)).isEqualTo("Hi there");
        // the invocation ends before the onCompleteResponse handler runs, so capture(...) can return before it
        ChatResponse response = completeResponse.get(10, SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("Hi there");
        assertThat(capture.invocation().lastResponse()).isEqualTo(response);
    }

    @Test
    void should_capture_strings_of_returned_publisher() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Good ", "morning"), bookings());

        AiServiceCapture capture = capture(() -> agent.chatReactive("Hi"));

        assertThat(capture.outputText()).isEqualTo("Good morning");
        List<Object> items = capture.lambdaOutputItems(); // the Strings a subscriber of the publisher received
        assertThat(items.stream().map(String.class::cast).collect(Collectors.joining())).isEqualTo("Good morning");
    }

    @Test
    void should_capture_publisher_subscribed_to_by_captured_code() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Good ", "morning"), bookings());
        List<String> items = new CopyOnWriteArrayList<>();

        AiServiceCapture capture = capture(() -> agent.chatReactive("Hi").subscribe(new CollectingSubscriber<>(items)));

        assertThat(capture.outputText()).isEqualTo("Good morning");
        assertThat(String.join("", items)).isEqualTo("Good morning");
    }

    @Test
    void should_capture_events_of_returned_event_publisher() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams(
                        toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"), AiMessage.from("Cancelled.")),
                bookings());

        AiServiceCapture capture = capture(() -> agent.chatWithEvents("Cancel MS-777"));

        assertThat(capture.outputText()).isEqualTo("Cancelled.");
        assertThat(capture.lambdaOutputItems())
                .extracting(event -> event.getClass().getSimpleName())
                .containsSubsequence(
                        "BeforeToolExecutionEvent",
                        "AfterToolExecutionEvent",
                        "PartialResponseEvent",
                        "FinalResponseEvent");
        assertThat(capture).hasToolExecutedTimes("cancelBooking", 1).hasOwnChatModelCallCount(2);
    }

    @Test
    void should_capture_event_publisher_subscribed_to_by_captured_code() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Hello"), bookings());
        List<AiServiceStreamingEvent> events = new CopyOnWriteArrayList<>();

        AiServiceCapture capture = capture(() -> agent.chatWithEvents("Hi").subscribe(new CollectingSubscriber<>(events)));

        assertThat(capture.outputText()).isEqualTo("Hello");
        assertThat(events).isNotEmpty();
    }

    // own chat model calls

    @Test
    void should_capture_own_chat_model_calls_of_tool_loop() {
        ChatModelMock model = ChatModelMock.thatResponds(callToolsThenAnswer(
                "MS-777 is cancelled.",
                toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}")));
        CustomerSupportAgent agent = agent(model);

        AiServiceCapture capture = capture(() -> agent.chat("klaus", "Cancel MS-777"));

        List<ChatModelCall> calls = capture.invocation().ownChatModelCalls();
        assertThat(calls).hasSize(3);
        assertThat(calls).allSatisfy(call -> assertThat(call.request().toolSpecifications())
                .extracting(ToolSpecification::name)
                .contains("getBookingDetails", "cancelBooking", "calculateRefund", "askPolicyExpert"));
        assertThat(calls)
                .extracting(call -> call.response().aiMessage().hasToolExecutionRequests())
                .containsExactly(true, true, false);
        assertThat(calls.get(2).response()).isEqualTo(capture.invocation().lastResponse());
        // the requests are those the model received
        assertThat(calls).extracting(ChatModelCall::request).containsExactlyElementsOf(model.requests());
        assertThat(capture).hasOwnChatModelCallCount(3).hasOwnChatModelCallCountAtMost(3).hasOwnChatModelCallCountAtMost(5);

        assertThatThrownBy(() -> assertThat(capture).hasOwnChatModelCallCount(1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 1 call(s) of the AI Service to its chat model, but there were 3.");
        assertThatThrownBy(() -> assertThat(capture).hasOwnChatModelCallCountAtMost(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at most 2 call(s) of the AI Service to its chat model, but there were 3.");
    }

    @Test
    void should_count_the_own_chat_model_call_of_output_guardrail_reprompt() {
        ChatModelMock model = ChatModelMock.thatResponds(request -> request.messages().size() <= 2
                ? AiMessage.from("Dude, it's gonna be fine")
                : AiMessage.from("Your booking is confirmed."));
        CustomerSupportAgent agent = agent(model);

        AiServiceCapture capture = capture(() -> agent.chat("klaus", "Is my booking ok?"));

        assertThat(capture.outputText()).isEqualTo("Your booking is confirmed.");
        assertThat(capture).hasNoToolExecutions().hasOwnChatModelCallCount(2);
    }

    // tool assertions

    @Test
    void should_check_how_often_and_in_which_order_tools_were_executed() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatResponds(callToolsThenAnswer(
                "MS-777 is cancelled, you get 148.5 EUR back.",
                toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"),
                toolCall("getBookingDetails", "{\"bookingNumber\":\"MS-777\"}"),
                toolCall("calculateRefund", "{\"bookingNumber\":\"MS-777\",\"days\":3}"))));

        AiServiceCapture capture = capture(() -> agent.chat("klaus", "Cancel MS-777 and refund me"));

        assertThat(capture)
                .hasToolExecutedTimes("getBookingDetails", 2)
                .hasToolExecutedTimes("cancelBooking", 1)
                .hasToolExecutedTimes("askPolicyExpert", 0)
                // other tools ran in between
                .hasToolsExecutedInOrder("getBookingDetails", "calculateRefund")
                .hasToolsExecutedInOrder("cancelBooking", "getBookingDetails", "calculateRefund");

        assertThatThrownBy(() -> assertThat(capture).hasToolExecutedTimes("cancelBooking", 2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected tool <cancelBooking> to be executed successfully 2 time(s), but it was 1 time(s).");
        assertThatThrownBy(() -> assertThat(capture).hasToolsExecutedInOrder("calculateRefund", "cancelBooking"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected these tools to be executed successfully in this order: [calculateRefund, cancelBooking], but they were not.");
        assertThatThrownBy(() -> assertThat(capture).hasToolExecutedTimes("cancelBooking", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("times must not be negative, but is: -1");
    }

    // hasSucceeded

    @Test
    void should_check_that_invocation_succeeded() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysThrowsExceptionWithMessage("Stream broken"), bookings());

        AiServiceCapture capture = captureExpectingFailure(() -> agent.chat("Hi").ignoreErrors().start());

        assertThatThrownBy(() -> assertThat(capture).hasSucceeded())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the invocation to succeed, but it failed with:")
                .hasMessageContaining("Stream broken");
    }

    @Test
    void should_fail_has_succeeded_for_cancelled_token_stream() {
        StreamingCustomerSupportAgent agent = CustomerSupport.streamingCustomerSupportAgent(
                StreamingChatModelMock.thatAlwaysStreams("Hello, this is a long answer"), bookings());

        AiServiceCapture capture = capture(() -> agent.chat("Hi")
                .onPartialResponseWithContext((partialResponse, context) -> context.streamingHandle().cancel())
                .onError(error -> {})
                .start());

        assertThatThrownBy(() -> assertThat(capture).hasSucceeded())
                .isInstanceOf(AssertionError.class)
                .hasMessage("[StreamingCustomerSupportAgent.chat] Expected the invocation to succeed, but it was cancelled");
    }

    // retrieved sources

    @Test
    void should_check_that_no_sources_were_retrieved() {
        PolicyExpert expert = CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("Hello!"));

        assertThat(capture(() -> expert.answer("Hi"))).hasNoRetrievedSources();

        AiServiceCapture pets = capture(() -> expert.answer("Can I bring my pets?"));
        assertThatThrownBy(() -> assertThat(pets).hasNoRetrievedSources())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no retrieved sources, but these were retrieved:")
                .hasMessageContaining("  - Pets: small pets are allowed");

        // the customer support agent has no retrieval augmentor
        AiServiceCapture agentRecording = capture(() -> agent(ChatModelMock.thatAlwaysResponds("Hello!")).chat("klaus", "Hi"));
        assertThatThrownBy(() -> assertThat(agentRecording).hasNoRetrievedSources())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no retrieved sources, but no sources were captured");
    }

    // several and nested invocations

    @Test
    void should_give_output_of_nested_invocation() {
        ChatModelMock agentModel = ChatModelMock.thatResponds(callToolsThenAnswer(
                results -> "According to our policy: " + results.get(0),
                toolCall("askPolicyExpert", "{\"question\":\"Can I bring my pets?\"}")));
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                agentModel,
                bookings(),
                CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("Small pets are allowed.")));

        AiServiceCapture capture = capture(() -> agent.chat("klaus", "Can I bring my pets?"));

        AiServiceInvocation nested = capture.invocation().nestedInvocations().get(0);
        assertThat(nested.output()).isEqualTo("Small pets are allowed.");
        assertThat(capture.invocations()).containsExactly(capture.invocation());
        assertThat(nested).hasSucceeded().hasOwnChatModelCallCount(1).hasRetrievedSourceContaining("small pets");
        assertThat(capture.outputText()).isEqualTo("According to our policy: Small pets are allowed.");
        assertThat(capture).topLevelOnly().hasOwnChatModelCallCount(2);
        assertThat(capture).includingNested().hasOwnChatModelCallCount(3);
        assertThatThrownBy(() -> capture.invocation().sources()).hasMessageContaining("has no retrieval augmentor");
    }

    @Test
    void should_give_each_of_several_invocations() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatResponds(callToolsThenAnswer(
                results -> results.isEmpty() ? "Hello!" : "Cancelled.",
                toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"))));
        BookingRequestExtractor extractor =
                CustomerSupport.bookingRequestExtractor(ChatModelMock.thatAlwaysResponds(EXTRACTED));

        AiServiceCapture capture = capture(() -> {
            BookingRequest request = extractor.extract(EMAIL);
            return agent.chat("klaus", "Please " + request.action() + " " + request.bookingNumber());
        });

        assertThat(capture.invocations()).hasSize(2);
        assertThat(capture.invocations().get(1).outputText()).isEqualTo("Cancelled.");
        assertThat(capture.invocations().get(1)).hasToolExecutedTimes("cancelBooking", 1);
        // each output by its type, as only one invocation produced a BookingRequest and only one a String
        assertThat(capture.output(BookingRequest.class)).isEqualTo(new BookingRequest("MS-777", "cancel", 2));
        assertThat(capture.output(String.class)).isEqualTo("Cancelled.");
        assertThat(capture.lambdaOutput()).isEqualTo("Cancelled.");
        assertThatThrownBy(capture::output).hasMessageContaining("Use output(Class)");
        assertThatThrownBy(capture::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Expected a single top-level AI Service invocation, but 2 were captured: ")
                .hasMessageContaining("com.example.support.BookingRequestExtractor.extract")
                .hasMessageContaining("com.example.support.CustomerSupportAgent.chat")
                .hasMessageEndingWith("Use invocations() to choose one.");
        assertThatThrownBy(capture::outputText).hasMessageEndingWith("Use invocations() to choose one.");
    }

    // judge with a custom question

    static final DecisionModelMock DECISION_MODEL = DecisionModelMock.thatAnswersYesNoQuestions(
            question -> question.text().contains("polite") ? 0.85 : 0.1);

    static final DecisionModelJudge SUPPORT_JUDGE = Judge.from(DECISION_MODEL)
            .withQuestion(criterion -> YesNoQuestion.builder()
                    .text("Would the Miles of Smiles support lead accept this response? It must: " + criterion.text())
                    .yesWhen("it clearly does")
                    .noWhen("it does not, or only partly")
                    .build());

    @Test
    void should_judge_output_text_with_custom_question() {
        CustomerSupportAgent agent = agent(ChatModelMock.thatAlwaysResponds("Hello Klaus, how can I help you today?"));

        AiServiceCapture capture = capture(() -> agent.chat("klaus", "Hi"));

        SUPPORT_JUDGE.assertThat(capture.outputText()).given("Hi").satisfies("is polite");

        YesNoQuestion question = (YesNoQuestion) DECISION_MODEL.request().questions().get("condition1");
        assertThat(question.text())
                .isEqualTo("Would the Miles of Smiles support lead accept this response? It must: is polite");
        assertThat(question.yesWhen()).isEqualTo("it clearly does");
        assertThat(question.noWhen()).isEqualTo("it does not, or only partly");
        assertThat(((FieldsInput) DECISION_MODEL.request().input()).fields())
                .isEqualTo(Map.of("userMessage", "Hi", "response", "Hello Klaus, how can I help you today?"));

        // the custom question is kept when the threshold changes
        assertThatThrownBy(() -> SUPPORT_JUDGE.withThreshold(0.9).assertThat(capture.outputText()).satisfies("is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Verdict: P(yes) = 0.850, threshold = 0.900");
        YesNoQuestion questionWithThreshold = (YesNoQuestion)
                DECISION_MODEL.requests().get(1).questions().get("condition1");
        assertThat(questionWithThreshold.text()).startsWith("Would the Miles of Smiles support lead accept");
    }

    static class CollectingSubscriber<T> implements Flow.Subscriber<T> {

        private final List<T> items;

        CollectingSubscriber(List<T> items) {
            this.items = items;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {}

        @Override
        public void onComplete() {}
    }
}
