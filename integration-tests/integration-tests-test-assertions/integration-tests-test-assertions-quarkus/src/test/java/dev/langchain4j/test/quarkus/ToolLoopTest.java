package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.quarkus.Models.toolCall;
import static dev.langchain4j.test.quarkus.Models.useChatModelResponding;
import static dev.langchain4j.test.quarkus.Models.useStreamingChatModel;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import com.example.support.CustomerContext;
import com.example.support.CustomerSupportAssistant;
import com.example.support.SupportService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.test.service.ChatModelCall;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ToolLoopTest {

    private static final String[] ALL_TOOLS = {"getOrderStatus", "cancelOrder", "escalateToHuman", "getCustomerTier"};

    @Inject
    CustomerSupportAssistant assistant;

    @Inject
    SupportService supportService;

    @Inject
    CustomerContext customerContext;

    @Test
    void records_one_own_chat_model_call_per_model_call_of_tool_loop() {
        MockChatModel chatModel = useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-2\"}"),
                toolCall("cancelOrder", "{\"orderNumber\":\"ORD-2\"}"),
                AiMessage.from("ORD-2 was still processing, so I cancelled it."));

        AiServiceCapture capture = capture(() -> supportService.answer("Cancel ORD-2 if it has not shipped yet"));

        AiServiceInvocation invocation = capture.invocation();
        assertThat(invocation.ownChatModelCalls()).hasSize(3).hasSameSizeAs(chatModel.requests());
        assertThat(invocation.ownChatModelCalls())
                .allSatisfy(call -> assertThat(call.request().toolSpecifications())
                        .extracting(ToolSpecification::name)
                        .containsExactlyInAnyOrder(ALL_TOOLS));
        assertThat(invocation.ownChatModelCalls().get(0).response().aiMessage().toolExecutionRequests())
                .singleElement()
                .satisfies(request -> assertThat(request.name()).isEqualTo("getOrderStatus"));
        assertThat(invocation.ownChatModelCalls().get(1).request().messages())
                .last()
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class, result -> assertThat(result.text())
                        .isEqualTo("processing"));
        assertThat(invocation.lastResponse()).isSameAs(invocation.ownChatModelCalls().get(2).response());
        assertThat(invocation.ownChatModelCalls())
                .extracting(ChatModelCall::request)
                .containsExactlyElementsOf(chatModel.requests());
        assertThat(capture)
                .hasOwnChatModelCallCount(3)
                .hasOwnChatModelCallCountAtMost(3)
                .hasToolsExecutedInOrder("getOrderStatus", "cancelOrder");
    }

    @Test
    void own_chat_model_call_count_fails_with_actual_count() {
        useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-1\"}"), AiMessage.from("ORD-1 has shipped."));

        AiServiceCapture capture = capture(() -> assistant.chat("Where is ORD-1?"));

        assertThatThrownBy(() -> assertThat(capture).hasOwnChatModelCallCount(1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 1 call(s) of the AI Service to its chat model, but there were 2")
                .hasMessageContaining("getOrderStatus");
        assertThatThrownBy(() -> assertThat(capture).hasOwnChatModelCallCountAtMost(1))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at most 1 call(s) of the AI Service to its chat model, but there were 2");
    }

    @Test
    void own_chat_model_calls_do_not_include_those_of_nested_invocation() {
        MockChatModel chatModel = useChatModelResponding(
                toolCall("escalateToHuman", "{\"issue\":\"The parcel arrived damaged\"}"),
                AiMessage.from("Customer received a damaged parcel."),
                AiMessage.from("I have handed your issue over to a colleague."));

        AiServiceCapture capture = capture(() -> assistant.chat("My parcel arrived damaged!"));

        assertThat(chatModel.requests()).hasSize(3);
        assertThat(capture).topLevelOnly().hasOwnChatModelCallCount(2);
        assertThat(capture).includingNested().hasOwnChatModelCallCount(3);
        assertThat(capture.invocation().nestedInvocations())
                .singleElement()
                .satisfies(nested -> assertThat(nested).hasOwnChatModelCallCount(1));
    }

    @Test
    void records_own_chat_model_calls_of_returned_uni() {
        useChatModelResponding(
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-1\"}"), AiMessage.from("ORD-1 has shipped."));

        AiServiceCapture capture = capture(() -> supportService.answerLater("Where is ORD-1?"));

        assertThat(capture).hasOwnChatModelCallCount(2).hasToolExecutedTimes("getOrderStatus", 1);
        assertThat(capture.invocation().ownChatModelCalls().get(0).request().toolSpecifications())
                .extracting(ToolSpecification::name)
                .contains("getOrderStatus");
    }

    @Test
    void records_own_chat_model_calls_of_returned_multi() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("Your ", "order ", "has ", "shipped."));

        AiServiceCapture capture = capture(() -> supportService.streamAnswer("Where is my order?"));

        assertThat(capture).hasOwnChatModelCallCount(1);
        assertThat(capture.invocation().ownChatModelCalls().get(0).response().aiMessage().text())
                .isEqualTo("Your order has shipped.");
    }

    @Test
    void counts_executions_of_request_scoped_tool() {
        customerContext.setCustomerId("VIP-7");
        useChatModelResponding(
                toolCall("getCustomerTier", "{}"),
                toolCall("getOrderStatus", "{\"orderNumber\":\"ORD-2\"}"),
                toolCall("getCustomerTier", "{}"),
                toolCall("cancelOrder", "{\"orderNumber\":\"ORD-2\"}"),
                AiMessage.from("As a gold customer, your order ORD-2 was cancelled for free."));

        AiServiceCapture capture = capture(() -> supportService.answer("Cancel ORD-2, I am a VIP"));

        assertThat(capture)
                .hasToolExecutedTimes("getCustomerTier", 2)
                .hasToolExecutedTimes("cancelOrder", 1)
                .hasToolExecutedTimes("escalateToHuman", 0)
                .hasToolsExecutedInOrder("getCustomerTier", "cancelOrder")
                .hasToolsExecutedInOrder("getCustomerTier", "getCustomerTier")
                .hasNoFailedToolExecutions()
                .hasOwnChatModelCallCount(5);
        assertThat(capture.invocation().toolExecutions())
                .filteredOn(execution -> execution.request().name().equals("getCustomerTier"))
                .extracting(execution -> execution.result())
                .containsExactly("gold", "gold");
    }

    @Test
    void tool_count_and_order_assertions_fail_with_executed_tools() {
        customerContext.setCustomerId("CUST-1");
        useChatModelResponding(
                toolCall("cancelOrder", "{\"orderNumber\":\"ORD-1\"}"),
                toolCall("getCustomerTier", "{}"),
                AiMessage.from("Done."));

        AiServiceCapture capture = capture(() -> supportService.answer("Cancel ORD-1"));

        assertThatThrownBy(() -> assertThat(capture).hasToolExecutedTimes("getCustomerTier", 2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected tool <getCustomerTier> to be executed successfully 2 time(s), but it was 1 time(s)");
        assertThatThrownBy(() -> assertThat(capture).hasToolsExecutedInOrder("getCustomerTier", "cancelOrder"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "Expected these tools to be executed successfully in this order: [getCustomerTier, cancelOrder], but they were not")
                .hasMessageContaining("cancelOrder")
                .hasMessageContaining("getCustomerTier");
    }

    @Test
    void has_no_retrieved_sources_for_question_the_policies_do_not_cover() {
        useChatModelResponding(AiMessage.from("Hello!"));

        AiServiceCapture capture = capture(() -> assistant.chat("Hi"));

        assertThat(capture).hasNoRetrievedSources().hasSucceeded();
    }

    @Test
    void has_no_retrieved_sources_fails_with_retrieved_policy() {
        useChatModelResponding(AiMessage.from("Within 30 days."));

        AiServiceCapture capture = capture(() -> assistant.chat("Can I return my shoes?"));

        assertThatThrownBy(() -> assertThat(capture).hasNoRetrievedSources())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected no retrieved sources, but these were retrieved")
                .hasMessageContaining("within 30 days of delivery");
    }

    @Test
    void has_no_retrieved_sources_fails_for_ai_service_without_content_retriever() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("Red ", "shoes"));

        AiServiceCapture capture = capture(() -> supportService.recommendProducts("Something red"));

        assertThatThrownBy(() -> assertThat(capture).hasNoRetrievedSources())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no sources were captured");
    }
}
