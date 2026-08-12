package dev.langchain4j.agentic.patterns.debate.example;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.debate.ConvergenceStrategy;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.patterns.debate.example.DebateAgents.DeontologicalDebater;
import dev.langchain4j.agentic.patterns.debate.example.DebateAgents.EthicsJudge;
import dev.langchain4j.agentic.patterns.debate.example.DebateAgents.EthicsPanel;
import dev.langchain4j.agentic.patterns.debate.example.DebateAgents.PragmatistDebater;
import dev.langchain4j.agentic.patterns.debate.example.DebateAgents.UtilitarianDebater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;

import static dev.langchain4j.agentic.patterns.Models.baseModel;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class DebateExampleIT {

    @Test
    void debate_reaches_verdict_on_ethical_question() {
        UtilitarianDebater d1 = AgenticServices.agentBuilder(UtilitarianDebater.class)
                .chatModel(baseModel())
                .outputKey("utilitarian")
                .build();

        DeontologicalDebater d2 = AgenticServices.agentBuilder(DeontologicalDebater.class)
                .chatModel(baseModel())
                .outputKey("deontological")
                .build();

        PragmatistDebater d3 = AgenticServices.agentBuilder(PragmatistDebater.class)
                .chatModel(baseModel())
                .outputKey("pragmatist")
                .build();

        EthicsJudge judge = AgenticServices.agentBuilder(EthicsJudge.class)
                .chatModel(baseModel())
                .outputKey("verdict")
                .build();

        EthicsPanel panel = AgenticServices.plannerBuilder(EthicsPanel.class)
                .subAgents(d1, d2, d3, judge)
                .outputKey("verdict")
                .planner(() -> new DebatePlanner(3, ConvergenceStrategy.unanimousLastWord()))
                .build();

        String result = panel.debate(
                "Is it ethical to use AI-generated art in commercial products without crediting the AI tool?");

        assertThat(result).isNotBlank();
        System.out.println("Final Verdict: " + result);

//            HtmlReportGenerator.generateReport(panel.agentMonitor(),
//                Path.of("src", "test", "resources", "debate-report.html"));
    }
}
