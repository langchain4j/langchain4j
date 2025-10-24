package dev.langchain4j.agentic.p2p.researcher;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.p2p.P2PPlanner;

import static dev.langchain4j.agentic.Models.baseModel;

public class P2PResearcherWithScorer {

    public static void main(String[] args) {
        ArxivCrawler arxivCrawler = new ArxivCrawler();

        LiteratureAgent literatureAgent = AgenticServices.agentBuilder(LiteratureAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputKey("researchFindings")
                .build();
        HypothesisAgent hypothesisAgent = AgenticServices.agentBuilder(HypothesisAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputKey("hypothesis")
                .build();
        CriticAgent criticAgent = AgenticServices.agentBuilder(CriticAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputKey("critique")
                .build();
        ValidationAgent validationAgent = AgenticServices.agentBuilder(ValidationAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputKey("hypothesis")
                .build();
        ScorerAgent scorerAgent = AgenticServices.agentBuilder(ScorerAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputKey("score")
                .build();

        ResearchAgent researcher = AgenticServices.plannerBuilder(ResearchAgent.class)
                .subAgents(literatureAgent, hypothesisAgent, criticAgent, validationAgent, scorerAgent)
                .outputKey("hypothesis")
                .build(() -> new P2PPlanner(10, agenticScope -> {
                    if (!agenticScope.hasState("score")) {
                        return false;
                    }
                    double score = agenticScope.readState("score", 0.0);
                    System.out.println("Current hypothesis score: " + score);
                    return score >= 0.85;
                }));

        System.out.println(researcher.research("black holes"));
    }
}
