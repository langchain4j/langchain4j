package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.quarkus.Models.toolCall;
import static dev.langchain4j.test.quarkus.Models.useChatModel;
import static dev.langchain4j.test.quarkus.Models.useChatModelResponding;
import static dev.langchain4j.test.quarkus.Models.useStreamingChatModel;

import com.example.support.CustomerContext;
import com.example.support.CustomerSupportAssistant;
import com.example.support.SupportService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ReactiveAssistantTest {

    @Inject
    CustomerSupportAssistant assistant;

    @Inject
    SupportService supportService;

    @Inject
    CustomerContext customerContext;

    @Test
    void subscribes_to_returned_uni() {
        MockChatModel chatModel = useChatModelResponding(AiMessage.from("Refunds take 5 days."));

        Uni<String> uni = supportService.answerLater("How long does a refund take?");
        assertThat(chatModel.requests()).as("the Uni is lazy").isEmpty();

        AiServiceInvocation invocation =
                AiServiceCapture.capture(() -> uni).invocation();

        assertThat(chatModel.requests()).hasSize(1);
        assertThat(invocation.invocationContext().methodName()).isEqualTo("chatAsync");
        assertThat(invocation.outputText()).isEqualTo("Refunds take 5 days.");
        assertThat(invocation).hasRetrievedSourceContaining("within 30 days");
    }

    @Test
    void subscribes_to_returned_uni_created_in_captured_code() {
        useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-2\"}"), AiMessage.from("ORD-2 is processing."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.answerLater("Where is ORD-2?"))
                .invocation();

        assertThat(invocation)
                .hasToolExecutedWith("getOrderStatus", Map.of("orderNumber", "ORD-2"))
                .hasNoFailedToolExecutions();
        assertThat(invocation.outputText()).isEqualTo("ORD-2 is processing.");
    }

    @Test
    void subscribes_to_returned_uni_chain() {
        useChatModelResponding(AiMessage.from("hello"));

        AiServiceInvocation invocation = AiServiceCapture.capture(() -> supportService
                        .answerLater("Hi")
                        .map(String::toUpperCase)
                        .onItem()
                        .transformToUni(answer -> Uni.createFrom().item(answer + "!")))
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("hello");
    }

    @Test
    void records_uni_awaited_inside_captured_code() {
        useChatModelResponding(AiMessage.from("Done"));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.answerLater("Hi").await().indefinitely())
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Done");
    }

    @Test
    void returned_completable_future_does_not_need_to_be_awaited() {
        useChatModelResponding(AiMessage.from("Async answer"));

        AiServiceInvocation invocation = AiServiceCapture.capture(() -> assistant.chatAsync("Hi"))
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Async answer");
    }

    @Test
    void records_nested_invocation_started_by_tool_of_asynchronous_invocation() {
        useChatModelResponding(
                toolCall("escalateToHuman", "{\"issue\":\"Wrong size delivered\"}"),
                AiMessage.from("Customer received the wrong size."),
                AiMessage.from("A colleague will contact you."));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.answerLater("I got the wrong size"))
                .invocation();

        assertThat(invocation).topLevelOnly().hasToolExecuted("escalateToHuman").hasNoFailedToolExecutions();
        assertThat(invocation.nestedInvocations())
                .singleElement()
                .satisfies(nested -> assertThat(nested.invocationContext().methodName()).isEqualTo("summarize"));
    }

    @Test
    void fails_asynchronous_invocation_when_tool_throws() {
        // unlike a blocking AI Service, an asynchronous one fails by default when a tool throws
        useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-404\"}"), AiMessage.from("Not found."));

        AiServiceInvocation invocation = AiServiceCapture.captureExpectingFailure(
                        () -> supportService.answerLater("Where is ORD-404?"))
                .invocation();

        assertThat(invocation)
                .hasFailedWithMessageContaining("Order ORD-404 not found")
                .hasToolExecutionFailed("getOrderStatus");
    }

    @Test
    void fails_asynchronous_invocation_when_request_scoped_tool_runs_without_request_context() {
        // the tool runs on another thread, where the request context of the test is not active
        customerContext.setCustomerId("VIP-1");
        useChatModelResponding(toolCall("getCustomerTier", "{}"), AiMessage.from("You are a gold customer."));

        AiServiceInvocation invocation = AiServiceCapture.captureExpectingFailure(
                        () -> supportService.answerLater("What is my tier?"))
                .invocation();

        assertThat(invocation)
                .hasFailedWith(ContextNotActiveException.class)
                .hasToolExecutionFailed("getCustomerTier");
    }

    @Test
    void rethrows_failure_of_returned_uni() {
        useChatModel(MockChatModel.thatFails(new RuntimeException("Model is overloaded")));

        assertThatThrownBy(() -> AiServiceCapture.capture(() -> supportService.answerLater("Hi")))
                .hasMessageContaining("Model is overloaded");
    }

    @Test
    void returns_failed_invocation_of_uni_when_failure_is_expected() {
        useChatModel(MockChatModel.thatFails(new RuntimeException("Model is overloaded")));

        AiServiceInvocation invocation = AiServiceCapture.captureExpectingFailure(
                        () -> supportService.answerLater("Hi"))
                .invocation();

        assertThat(invocation).hasFailedWithMessageContaining("Model is overloaded");
    }

    @Test
    void returns_failed_invocation_when_uni_failure_was_recovered() {
        useChatModel(MockChatModel.thatFails(new RuntimeException("Model is overloaded")));

        AiServiceInvocation invocation = AiServiceCapture.captureExpectingFailure(() -> supportService
                        .answerLater("Hi")
                        .onFailure()
                        .recoverWithItem("Sorry, try again later"))
                .invocation();

        assertThat(invocation).hasFailedWithMessageContaining("Model is overloaded");
    }

    @Test
    void drains_returned_multi() {
        useStreamingChatModel(MockStreamingChatModel.thatStreams(ChatResponse.builder()
                .aiMessage(AiMessage.from("Your order has shipped."))
                .tokenUsage(new TokenUsage(10, 4))
                .finishReason(FinishReason.STOP)
                .build()));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.streamAnswer("Where is my order?"))
                .invocation();

        assertThat(invocation.invocationContext().methodName()).isEqualTo("chatStream");
        assertThat(invocation.outputText()).isEqualTo("Your order has shipped.");
        assertThat(invocation).hasFinishReason(FinishReason.STOP);
        assertThat(invocation.tokenUsage().totalTokenCount()).isLessThanOrEqualTo(14);
        assertThat(invocation.isCancelled()).isFalse();
    }

    @Test
    void drains_returned_flow_publisher_of_ai_service() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("Hi ", "there"));

        AiServiceInvocation invocation =
                AiServiceCapture.capture(() -> assistant.chatStream("Hi")).invocation();

        assertThat(invocation.outputText()).isEqualTo("Hi there");
    }

    @Test
    void records_retrieved_sources_of_streaming_invocation() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("30 ", "days"));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.streamAnswer("What is the return period?"))
                .invocation();

        assertThat(invocation).hasRetrievedSourceContaining("within 30 days");
    }

    @Test
    void subscribes_to_returned_multi_collected_to_uni() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("a", "b", "c"));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.streamAnswer("Hi").collect().asList())
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("abc");
    }

    @Test
    void emits_guarded_streaming_answer_as_a_single_item() {
        // the output guardrail must see the whole answer, so the streamed answer is emitted once it is validated
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("a", "b", "c"));
        List<String> items = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = AiServiceCapture.capture(() -> items.addAll(
                        supportService.streamAnswer("Hi").collect().asList().await().indefinitely()))
                .invocation();

        assertThat(items).containsExactly("abc");
        assertThat(invocation.outputText()).isEqualTo("abc");
    }

    @Test
    void records_multi_subscribed_inside_captured_code() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("a", "b", "c"));
        List<String> items = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = AiServiceCapture.capture(() -> items.addAll(
                        supportService.recommendProducts("Hi").collect().asList().await().indefinitely()))
                .invocation();

        assertThat(items).containsExactly("a", "b", "c");
        assertThat(invocation.invocationContext().methodName()).isEqualTo("recommend");
        assertThat(invocation.outputText()).isEqualTo("abc");
    }

    @Test
    void records_multi_subscribed_with_callbacks_inside_captured_code() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("x", "y"));
        List<String> items = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.recommendProducts("Hi").subscribe().with(items::add))
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("xy");
        assertThat(items).containsExactly("x", "y");
    }

    @Test
    void drains_returned_multi_item_by_item() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("Red ", "shoes"));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.recommendProducts("Something red"))
                .invocation();

        assertThat(invocation.outputText()).isEqualTo("Red shoes");
        assertThatThrownBy(invocation::sources)
                .as("the advisor has no content retriever")
                .hasMessageContaining("has no retrieval augmentor");
    }

    @Test
    void records_cancelled_invocation_when_returned_multi_takes_first_item() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("one ", "two ", "three"));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.recommendProducts("Count").select().first())
                .invocation();

        assertThat(invocation.isCancelled()).isTrue();
        assertThat(invocation.error()).isNull();
        assertThatThrownBy(invocation::outputText)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("cancel");
        assertThatThrownBy(() -> assertThat(invocation).hasNoToolExecutions())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("cancel");
    }

    @Test
    void records_cancelled_invocation_when_returned_uni_takes_first_item_of_multi() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("one ", "two ", "three"));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.recommendProducts("Count").toUni())
                .invocation();

        assertThat(invocation.isCancelled()).isTrue();
    }

    @Test
    void records_cancelled_invocation_when_multi_is_cancelled_inside_captured_code() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("one ", "two ", "three"));

        AiServiceInvocation invocation = AiServiceCapture.capture(() -> supportService
                        .recommendProducts("Count")
                        .select()
                        .first(3)
                        .collect()
                        .asList()
                        .await()
                        .indefinitely())
                .invocation();

        assertThat(invocation.isCancelled()).isTrue();
    }

    @Test
    void does_not_cancel_guarded_answer_when_returned_multi_takes_first_item() {
        // the guarded answer is a single item, so taking the first item takes the whole answer
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("one ", "two"));

        AiServiceInvocation invocation = AiServiceCapture.capture(
                        () -> supportService.streamAnswer("Count").select().first())
                .invocation();

        assertThat(invocation.isCancelled()).isFalse();
        assertThat(invocation.outputText()).isEqualTo("one two");
    }

    @Test
    void rethrows_failure_of_returned_multi() {
        useStreamingChatModel(MockStreamingChatModel.thatFails(new RuntimeException("Stream broke")));

        assertThatThrownBy(() -> AiServiceCapture.capture(() -> supportService.streamAnswer("Hi")))
                .hasMessageContaining("Stream broke");
    }

    @Test
    void returns_failed_invocation_of_multi_when_failure_is_expected() {
        useStreamingChatModel(MockStreamingChatModel.thatFails(new RuntimeException("Stream broke")));

        AiServiceInvocation invocation = AiServiceCapture.captureExpectingFailure(
                        () -> supportService.streamAnswer("Hi"))
                .invocation();

        assertThat(invocation).hasFailedWithMessageContaining("Stream broke");
    }

    @Test
    void does_not_capture_uni_that_is_never_subscribed() {
        MockChatModel chatModel = useChatModelResponding(AiMessage.from("never"));

        AiServiceCapture invocations = AiServiceCapture.capture(() -> {
            supportService.answerLater("Hi"); // the Uni is dropped, so the AI Service is never called
        });

        assertThat(invocations.invocations()).isEmpty();
        assertThat(chatModel.requests()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured.");
    }

    @Test
    void hint_for_unsubscribed_uni_mentions_uni() {
        useChatModelResponding(AiMessage.from("never"));

        AiServiceCapture invocations = AiServiceCapture.capture(() -> {
            supportService.answerLater("Hi");
        });

        assertThatThrownBy(invocations::invocation).hasMessageContaining("Uni");
    }

    @Test
    void does_not_capture_uni_subscribed_on_another_thread() {
        useChatModelResponding(AiMessage.from("Answer from a worker thread"));

        AiServiceCapture invocations = AiServiceCapture.capture(() -> supportService
                .answerLater("Hi")
                .runSubscriptionOn(Infrastructure.getDefaultExecutor()));

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Invocations started on other threads are not captured");
    }

    @Test
    void hint_for_uni_subscribed_on_another_thread_mentions_mutiny_operator() {
        useChatModelResponding(AiMessage.from("Answer from a worker thread"));

        AiServiceCapture invocations = AiServiceCapture.capture(() -> supportService
                .answerLater("Hi")
                .runSubscriptionOn(Infrastructure.getDefaultExecutor()));

        assertThatThrownBy(invocations::invocation).hasMessageContaining("runSubscriptionOn");
    }
}
