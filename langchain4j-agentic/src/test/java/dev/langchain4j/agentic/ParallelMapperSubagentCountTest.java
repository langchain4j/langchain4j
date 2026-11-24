package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

class ParallelMapperSubagentCountTest {

    static final ChatModel DUMMY_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("test-response"))
                    .build();
        }
    };

    public interface ItemAgent {

        @UserMessage("Map: {{item}}")
        @Agent(description = "Maps a single item", outputKey = "mapped")
        String map(@V("item") String item);
    }

    public interface BatchAgent {

        @Agent
        String[] run(@V("items") String... items);
    }

    @Test
    void build_fails_when_no_subagent() {
        assertThatThrownBy(() ->
                        AgenticServices.parallelMapperBuilder(BatchAgent.class).build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("exactly one subagent")
                .hasMessageContaining("0");
    }

    @Test
    void build_fails_when_more_than_one_subagent() {
        assertThatThrownBy(() -> AgenticServices.parallelMapperBuilder(BatchAgent.class)
                        .subAgents(itemAgent(), itemAgent())
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("exactly one subagent")
                .hasMessageContaining("2");
    }

    private static ItemAgent itemAgent() {
        return AgenticServices.agentBuilder(ItemAgent.class)
                .chatModel(DUMMY_MODEL)
                .outputKey("mapped")
                .build();
    }
}
