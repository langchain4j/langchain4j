package dev.langchain4j.agentic.patterns.goap;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.patterns.goap.writer.WriterAgents;
import dev.langchain4j.agentic.patterns.Models;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class GoalOrientedNullPreconditionTest {

    @Test
    void customListenerInjectsVariable_notInGraph_noNPE() {
        class VariableInjectListener implements dev.langchain4j.agentic.observability.AgentListener {
            @Override
            public void afterAgentInvocation(dev.langchain4j.agentic.observability.AgentResponse response) {
                // Inject a variable that is not part of any agent's arguments
                response.agenticScope().writeState("extraVar", "value");
            }
        }

        // Simple agents with no arguments
        var dummy = AgenticServices.agentBuilder(dev.langchain4j.agentic.patterns.goap.writer.WriterAgents.StoryGenerator.class)
                .chatModel(Models.baseModel())
                .outputKey("story")
                .build();

        UntypedAgent planner = AgenticServices.plannerBuilder()
                .subAgents(dummy)
                .outputKey("story")
                .planner(GoalOrientedPlanner::new)
                .listener(new VariableInjectListener())
                .build();

        var result = planner.invoke(Map.of());
        assertThat(result).isNotNull();
    }
}
