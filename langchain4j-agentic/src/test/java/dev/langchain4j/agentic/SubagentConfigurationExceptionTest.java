package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

/**
 * Verifies that agentic builder configuration errors surface as the correct exception type
 * rather than being wrapped in UndeclaredThrowableException.
 *
 * <p>Before the fix, {@link dev.langchain4j.agentic.agent.AgentInvocationHandler#invoke}
 * did not unwrap InvocationTargetException when dispatching {@code method.invoke(this, args)}
 * for AgentInstance and InternalAgent methods, so the JDK proxy rewrapped it in
 * UndeclaredThrowableException with no message.
 */
class SubagentConfigurationExceptionTest {

    interface MemoryAgent {
        @Agent
        String call(@V("item") String item);
    }

    interface BatchAgent {
        @Agent
        String[] run(@V("items") String... items);
    }

    static final ChatModel FAILING_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            throw new RuntimeException("model failure");
        }
    };

    @Test
    void parallelMapper_subagent_with_chatMemory_throws_AgenticSystemConfigurationException() {
        // Given: a subagent with chat memory (not allowed as a subagent)
        MemoryAgent subagent = AgenticServices.agentBuilder(MemoryAgent.class)
                .chatModel(FAILING_MODEL)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("result")
                .build();

        // When/Then: the builder should fail with the correct exception type and a clear message
        assertThatThrownBy(() -> AgenticServices.parallelMapperBuilder(BatchAgent.class)
                        .subAgents(subagent)
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("Agents with chat memory can't be a subagent")
                .hasMessageContaining("BatchAgent");
    }

    @Test
    void subagent_without_chatMemory_is_accepted_and_keeps_its_return_values() {
        MemoryAgent subagent = AgenticServices.agentBuilder(MemoryAgent.class)
                .chatModel(FAILING_MODEL)
                .outputKey("result")
                .build();

        BatchAgent batchAgent = AgenticServices.parallelMapperBuilder(BatchAgent.class)
                .subAgents(subagent)
                .build();

        // The unwrapping must not swallow ordinary results: AgentInstance/InternalAgent methods
        // dispatched through the same branch still return their values.
        assertThat(batchAgent).isNotNull();
        assertThat(((AgentInstance) batchAgent).subagents()).hasSize(1);
    }
}
