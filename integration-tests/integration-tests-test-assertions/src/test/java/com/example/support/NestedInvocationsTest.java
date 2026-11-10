package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.callToolsThenAnswer;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * An AI Service (the policy expert) used as a tool by another one (the customer support agent).
 */
@Timeout(30)
class NestedInvocationsTest {

    ChatModelMock agentModel = ChatModelMock.thatResponds(callToolsThenAnswer(
                    results -> "According to our policy: " + results.get(0),
                    toolCall("askPolicyExpert", "{\"question\":\"Can I cancel MS-777 for free?\"}")))
            .withTokenUsage(new TokenUsage(10, 1));

    @Test
    void should_capture_invocation_started_by_a_tool_as_nested() {
        ChatModelMock expertModel = ChatModelMock.thatAlwaysResponds("Yes, up to 7 days before the start date.")
                .withTokenUsage(new TokenUsage(1000, 100));
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                agentModel, bookings(), CustomerSupport.policyExpert(expertModel));

        // only the invocation started by the lambda is a top-level invocation
        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Can I cancel MS-777 for free?")).invocation();

        assertThat(invocation.outputText())
                .isEqualTo("According to our policy: Yes, up to 7 days before the start date.");
        assertThat(invocation)
                .topLevelOnly()
                .hasToolsExecutedExactlyInAnyOrder("askPolicyExpert")
                .hasToolExecutedWith("askPolicyExpert", Map.of("question", "Can I cancel MS-777 for free?"))
                .hasNoFailedToolExecutions()
                .hasModelCallCount(2);
        assertThat(invocation).includingNested().hasModelCallCount(3);
        assertThatThrownBy(() -> assertThat(invocation).hasModelCallCount(2))
                .hasMessageContaining("This invocation has 1 nested invocations. Use includingNested() or topLevelOnly()");

        assertThat(invocation.nestedInvocations()).singleElement().satisfies(nested -> {
            assertThat(nested.invocationContext().methodName()).isEqualTo("answer");
            assertThat(nested.invocationContext().interfaceName()).isEqualTo(PolicyExpert.class.getName());
            assertThat(nested.invocationContext().parentInvocationId()).isEqualTo(invocation.invocationContext().invocationId());
            assertThat(nested.outputText()).isEqualTo("Yes, up to 7 days before the start date.");
            assertThat(nested.tokenUsage().totalTokenCount()).isEqualTo(1100);
            assertThat(nested).hasRetrievedSourceContaining("free of charge up to 7 days");
        });
    }

    @Test
    void should_not_include_sources_of_nested_invocations() {
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                agentModel, bookings(), CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("Yes.")));

        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Can I cancel MS-777 for free?")).invocation();

        // the agent itself has no retrieval augmentor
        assertThatThrownBy(invocation::sources).hasMessageContaining("has no retrieval augmentor");
        assertThat(invocation).includingNested().hasRetrievedSourceContaining("7 days");
        assertThatThrownBy(() -> assertThat(invocation).topLevelOnly().hasRetrievedSourceContaining("7 days"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected a retrieved source containing <7 days>, but no sources were captured");
        assertThat(invocation.nestedInvocations().get(0).sources()).isNotEmpty();
    }

    @Test
    void should_not_rethrow_failure_of_nested_invocation_handled_by_the_tool() {
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                ChatModelMock.thatResponds(callToolsThenAnswer(
                        "Sorry, I cannot check the policy right now.",
                        toolCall("askPolicyExpert", "{\"question\":\"Can I bring my dog?\"}"))),
                bookings(),
                CustomerSupport.policyExpert(
                        ChatModelMock.thatAlwaysThrows(new RuntimeException("Policy model overloaded"))));

        // the tool failure is reported to the agent's model, which answers anyway
        AiServiceInvocation invocation = capture(() -> agent.chat("klaus", "Can I bring my dog?")).invocation();

        assertThat(invocation.outputText()).isEqualTo("Sorry, I cannot check the policy right now.");
        assertThat(invocation).topLevelOnly().hasToolExecutionFailed("askPolicyExpert");
        assertThatThrownBy(() -> assertThat(invocation).topLevelOnly().hasNoFailedToolExecutions())
                .hasMessageContaining("askPolicyExpert({\"question\":\"Can I bring my dog?\"}) -> ")
                .hasMessageContaining("(FAILED)");
        AiServiceInvocation nested = invocation.nestedInvocations().get(0);
        assertThat(nested.error()).hasMessageContaining("Policy model overloaded");
        assertThat(nested).hasFailedWithMessageContaining("Policy model overloaded");
    }

    interface Triage {

        String route(String message);
    }

    static class Escalation {

        private final CustomerSupportAgent agent;

        Escalation(CustomerSupportAgent agent) {
            this.agent = agent;
        }

        @Tool("Hands the conversation over to the customer support agent")
        String escalate(@P(name = "message", description = "The customer's message") String message) {
            return agent.chat("escalated", message);
        }
    }

    @Test
    void should_tell_that_tools_of_nested_invocations_are_not_included() {
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                ChatModelMock.thatResponds(callToolsThenAnswer(
                        "MS-777 is cancelled.", toolCall("cancelBooking", "{\"bookingNumber\":\"MS-777\"}"))),
                bookings(),
                CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("unused")));
        Triage triage = AiServices.builder(Triage.class)
                .chatModel(ChatModelMock.thatResponds(callToolsThenAnswer(
                        results -> results.get(0), toolCall("escalate", "{\"message\":\"Cancel MS-777\"}"))))
                .tools(new Escalation(agent))
                .build();

        AiServiceInvocation invocation = capture(() -> triage.route("Cancel MS-777")).invocation();

        assertThat(invocation.outputText()).isEqualTo("MS-777 is cancelled.");
        assertThatThrownBy(() -> assertThat(invocation).topLevelOnly().hasToolExecuted("cancelBooking"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected tool <cancelBooking> to be executed successfully, but it was not executed.")
                .hasMessageContaining(
                        "Nested invocations (see nestedInvocations()) executed these tools, which are not included: [cancelBooking]");
        assertThat(invocation.nestedInvocations().get(0)).hasToolExecuted("cancelBooking");
        assertThat(invocation).includingNested().hasToolsExecutedExactlyInAnyOrder("escalate", "cancelBooking");
    }
}
