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

class AgentInvocationHandlerTest {

    static final ChatModel FAILING_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            throw new RuntimeException("model failure");
        }
    };

    interface BatchAgent {

        @Agent
        String[] run(@V("items") String... items);
    }

    interface MemoryAgent {

        @Agent
        String call(@V("item") String item);
    }

    private static MemoryAgent subagent(boolean withChatMemory) {
        var builder = AgenticServices.agentBuilder(MemoryAgent.class)
                .chatModel(FAILING_MODEL)
                .outputKey("result");
        if (withChatMemory) {
            builder.chatMemory(MessageWindowChatMemory.withMaxMessages(10));
        }
        return builder.build();
    }

    @Test
    void configuration_exception_from_internal_agent_method_is_propagated_unwrapped() {
        MemoryAgent subagent = subagent(true);

        assertThatThrownBy(() -> AgenticServices.parallelMapperBuilder(BatchAgent.class)
                        .subAgents(subagent)
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("Agents with chat memory can't be a subagent")
                .hasMessageContaining("BatchAgent");
    }

    @Test
    void internal_agent_methods_still_return_their_values() {
        BatchAgent batchAgent = AgenticServices.parallelMapperBuilder(BatchAgent.class)
                .subAgents(subagent(false))
                .build();

        AgentInstance agentInstance = (AgentInstance) batchAgent;
        assertThat(agentInstance.subagents()).hasSize(1);
        assertThat(agentInstance.subagents().get(0).outputKey()).isEqualTo("result");
        assertThat(agentInstance.async()).isFalse();
    }
}
