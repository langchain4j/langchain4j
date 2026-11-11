package dev.langchain4j.agentic;

import static dev.langchain4j.agentic.Models.baseModel;
import static dev.langchain4j.agentic.Models.streamingBaseModel;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.declarative.ToolSearchStrategySupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AgentToolSearchIT {

    private static final String REQUEST = "Find the current weather using tools. Search for a weather tool if needed.";

    public interface Assistant {
        @UserMessage("{{request}}")
        @Agent(outputKey = "answer")
        String answer(@V("request") String request);
    }

    public interface DeclarativeStreamingAssistant {
        @UserMessage("{{request}}")
        @Agent(outputKey = "answer")
        TokenStream answer(@V("request") String request);

        @ToolsSupplier
        static Object tools() {
            return new WeatherTools();
        }

        @ToolSearchStrategySupplier
        static ToolSearchStrategy toolSearchStrategy() {
            return new SimpleToolSearchStrategy();
        }
    }

    public static class WeatherTools {
        @Tool("Find the current weather")
        String weather() {
            return "sunny";
        }
    }

    @Test
    void should_discover_and_execute_a_tool() {
        List<String> executedTools = new CopyOnWriteArrayList<>();
        Assistant agent = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(baseModel())
                .tools(new WeatherTools())
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .listener(recordTools(executedTools))
                .build();

        assertThat(agent.answer(REQUEST)).containsIgnoringCase("sunny");
        assertThat(executedTools).contains("tool_search_tool", "weather");
    }

    @Test
    void should_discover_and_execute_a_tool_with_a_declarative_streaming_agent() throws Exception {
        List<String> executedTools = new CopyOnWriteArrayList<>();
        DeclarativeStreamingAssistant agent = AgenticServices.agentBuilder(DeclarativeStreamingAssistant.class)
                .streamingChatModel(streamingBaseModel())
                .listener(recordTools(executedTools))
                .build();
        CompletableFuture<ChatResponse> result = new CompletableFuture<>();

        agent.answer(REQUEST)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(result::complete)
                .onError(result::completeExceptionally)
                .start();

        assertThat(result.get(60, SECONDS).aiMessage().text()).containsIgnoringCase("sunny");
        assertThat(executedTools).contains("tool_search_tool", "weather");
    }

    private static AgentListener recordTools(List<String> executedTools) {
        return new AgentListener() {
            @Override
            public void afterAgentToolExecution(AfterAgentToolExecution event) {
                executedTools.add(event.toolExecution().request().name());
            }
        };
    }
}
