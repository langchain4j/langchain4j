package dev.langchain4j.agentic.researcher;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.p2p.P2PAgent;

import static dev.langchain4j.agentic.Models.baseModel;

public class P2PResearcherUntyped {

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

        P2PAgent researcher = AgenticServices.p2pBuilder()
                .chatModel(baseModel())
                .subAgents(literatureAgent, hypothesisAgent, criticAgent, validationAgent, scorerAgent)
                .outputKey("hypothesis")
                .exitCondition( agenticScope -> {
                    if (!agenticScope.hasState("score")) {
                        return false;
                    }
                    double score = agenticScope.readState("score", 0.0);
                    System.out.println("Current hypothesis score: " + score);
                    return score >= 0.85;
                })
                .maxAgentsInvocations(10)
                .build();

        System.out.println(researcher.invoke("produce a research about black holes"));
    }
}
