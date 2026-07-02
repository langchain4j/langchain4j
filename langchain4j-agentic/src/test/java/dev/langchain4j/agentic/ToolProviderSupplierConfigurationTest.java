package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolProviderSupplierConfigurationTest {

    private static final AtomicInteger toolProviderSupplierCalls = new AtomicInteger();

    @BeforeEach
    void reset() {
        toolProviderSupplierCalls.set(0);
    }

    @Test
    void should_apply_declarative_tool_provider_supplier_once_when_creating_single_agent_system() {
        AgentWithToolProvider agent = AgenticServices.createAgenticSystem(AgentWithToolProvider.class);

        String response = agent.chat("hello");

        assertThat(response).isEqualTo("ok");
        assertThat(toolProviderSupplierCalls).hasValue(1);
    }

    @Test
    void should_apply_declarative_tool_provider_supplier_once_when_creating_sub_agent() {
        SequenceWithToolProviderSubAgent agent = AgenticServices.createAgenticSystem(
                SequenceWithToolProviderSubAgent.class, AgentWithToolProvider.chatModel());

        String response = agent.chat("hello");

        assertThat(response).isEqualTo("ok");
        assertThat(toolProviderSupplierCalls).hasValue(1);
    }

    public interface SequenceWithToolProviderSubAgent {

        @SequenceAgent(
                outputKey = "response",
                subAgents = {AgentWithToolProvider.class})
        String chat(@V("message") String message);
    }

    public interface AgentWithToolProvider {

        @UserMessage("Say {{message}}")
        @Agent(outputKey = "response")
        String chat(@V("message") String message);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse chat(ChatRequest chatRequest) {
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from("ok"))
                            .build();
                }
            };
        }

        @ToolProviderSupplier
        static ToolProvider toolProvider() {
            toolProviderSupplierCalls.incrementAndGet();
            ToolSpecification tool = ToolSpecification.builder()
                    .name("test_tool")
                    .description("A test tool")
                    .build();
            return request -> ToolProviderResult.builder()
                    .add(tool, (toolExecutionRequest, memoryId) -> "tool result")
                    .build();
        }
    }
}
