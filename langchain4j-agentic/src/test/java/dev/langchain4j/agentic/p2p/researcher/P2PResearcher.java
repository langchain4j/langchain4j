package dev.langchain4j.agentic.p2p.researcher;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.p2p.P2PPlanner;

import static dev.langchain4j.agentic.Models.baseModel;

public class P2PResearcher {

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

        ResearchAgent researcher = AgenticServices.plannerBuilder(ResearchAgent.class)
                .subAgents(literatureAgent, hypothesisAgent, criticAgent, validationAgent)
                .outputKey("hypothesis")
                .build(P2PPlanner::new);

        System.out.println(researcher.research("black holes"));
    }
}
