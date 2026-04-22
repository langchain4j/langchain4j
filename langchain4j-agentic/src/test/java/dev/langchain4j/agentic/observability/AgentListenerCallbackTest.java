package dev.langchain4j.agentic.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Test for AgentListener callbacks in AgentInvocationHandler
 *
 * <p>
 * Verifies that beforeAgentInvocation and afterAgentInvocation callbacks
 * are triggered when Agent is invoked directly (non-Planner mode).
 * </p>
 */
class AgentListenerCallbackTest {

    /**
     * Simple Agent interface for testing
     */
    public interface SimpleTestAgent {
        @Agent(value = "Test agent for callback verification", outputKey = "result")
        @UserMessage("Process: {{input}}")
        String execute(@V("input") String input);
    }

    /**
     * Test Tool
     */
    public static class TestTool {
        @Tool("Convert text to uppercase")
        public String toUpperCase(String text) {
            return text != null ? text.toUpperCase() : "NULL";
        }
    }

    /**
     * Mock ChatModel that simulates tool calling
     */
    static class MockToolCallingChatModel implements ChatModel {
        private final String toolNameToCall;
        private int callCount = 0;

        public MockToolCallingChatModel(String toolNameToCall) {
            this.toolNameToCall = toolNameToCall;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            callCount++;

            // First call: return tool execution request
            if (callCount == 1) {
                AiMessage aiMessage = AiMessage.builder()
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .name(toolNameToCall)
                                .arguments("{\"text\": \"hello\"}")
                                .build()))
                        .build();

                return ChatResponse.builder().aiMessage(aiMessage).build();
            }

            // Second call: return final result
            AiMessage finalMessage =
                    AiMessage.builder().text("Tool executed, result: HELLO").build();
            return ChatResponse.builder().aiMessage(finalMessage).build();
        }
    }

    /**
     * Test AgentListener to verify callback invocation
     */
    static class TestAgentListener implements AgentListener {
        private final AtomicBoolean beforeInvocationCalled = new AtomicBoolean(false);
        private final AtomicBoolean afterInvocationCalled = new AtomicBoolean(false);
        private final AtomicBoolean beforeToolExecutionCalled = new AtomicBoolean(false);
        private final AtomicBoolean afterToolExecutionCalled = new AtomicBoolean(false);

        private final AtomicReference<AgentRequest> capturedAgentRequest = new AtomicReference<>();
        private final AtomicReference<AgentResponse> capturedAgentResponse = new AtomicReference<>();

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            beforeInvocationCalled.set(true);
            capturedAgentRequest.set(agentRequest);
            System.out.println("[TestListener] beforeAgentInvocation called: agent=" + agentRequest.agentName());
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            afterInvocationCalled.set(true);
            capturedAgentResponse.set(agentResponse);
            System.out.println("[TestListener] afterAgentInvocation called: agent=" + agentResponse.agentName());
        }

        @Override
        public void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {
            beforeToolExecutionCalled.set(true);
            System.out.println("[TestListener] beforeAgentToolExecution called: tool="
                    + beforeAgentToolExecution.toolExecution().request().name());
        }

        @Override
        public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
            afterToolExecutionCalled.set(true);
            System.out.println("[TestListener] afterAgentToolExecution called: tool="
                    + afterAgentToolExecution.toolExecution().request().name());
        }

        public AtomicBoolean getBeforeInvocationCalled() {
            return beforeInvocationCalled;
        }

        public AtomicBoolean getAfterInvocationCalled() {
            return afterInvocationCalled;
        }

        public AtomicBoolean getBeforeToolExecutionCalled() {
            return beforeToolExecutionCalled;
        }

        public AtomicBoolean getAfterToolExecutionCalled() {
            return afterToolExecutionCalled;
        }
    }

    /**
     * Test: Bug reproduction - Verify that AgentListener callbacks are NOT triggered when Agent is invoked directly
     * This test reproduces the bug where beforeAgentInvocation and afterAgentInvocation callbacks
     * are not called in AgentInvocationHandler
     */
    @Test
    void testAgentListenerCallbacksNotCalledBug() {
        // Given: Create Agent with listener
        MockToolCallingChatModel mockChatModel = new MockToolCallingChatModel("toUpperCase");
        TestAgentListener listener = new TestAgentListener();

        SimpleTestAgent agent = AgenticServices.agentBuilder(SimpleTestAgent.class)
                .chatModel(mockChatModel)
                .tools(new TestTool())
                .listener(listener)
                .build();

        // When: Invoke Agent method directly
        agent.execute("hello");

        // Then: Verify tool callbacks are triggered (these work correctly)
        assertThat(listener.getBeforeToolExecutionCalled().get())
                .as("beforeAgentToolExecution should be called")
                .isTrue();

        assertThat(listener.getAfterToolExecutionCalled().get())
                .as("afterAgentToolExecution should be called")
                .isTrue();

        // Then: Verify Agent invocation callbacks are NOT triggered (this demonstrates the bug)
        // These assertions will fail until the bug is fixed
        assertThat(listener.getBeforeInvocationCalled().get())
                .as("beforeAgentInvocation should be called but is not (BUG)")
                .isTrue();

        assertThat(listener.getAfterInvocationCalled().get())
                .as("afterAgentInvocation should be called but is not (BUG)")
                .isTrue();

        System.out.println("Bug reproduction test completed");
    }
}
