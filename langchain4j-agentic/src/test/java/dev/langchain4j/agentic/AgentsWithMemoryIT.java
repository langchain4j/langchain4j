package dev.langchain4j.agentic;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.agentic.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
public class AgentsWithMemoryIT {

    public interface Assistant {
        @SystemMessage("You are an agent building robots. You need to understand how many robot needs to be built.")
        @UserMessage("{{request}}")
        @Agent
        String processData(@V("request") String request);
    }

    @Test
    public void agent_with_default_chat_memory_test() {
        Assistant assistant = AgenticServices.agentBuilder(Assistant.class)
                .chatModel(baseModel())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .outputKey("responses")
                .build();

        UntypedAgent sequenceAgent = AgenticServices.sequenceBuilder()
                .subAgents(assistant)
                .outputKey("responses")
                .build();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("request", "Build a robot");

        String result = (String) sequenceAgent.invoke(parameters);
        assertThat(result).containsIgnoringCase("robot");
    }
}
