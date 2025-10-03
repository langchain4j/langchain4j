package dev.langchain4j.agentic.researcher;

import dev.langchain4j.agentic.AgenticServices;

import static dev.langchain4j.agentic.Models.baseModel;

public class P2PResearcher {

    public static void main(String[] args) {
        ArxivCrawler arxivCrawler = new ArxivCrawler();

        LiteratureAgent literatureAgent = AgenticServices.agentBuilder(LiteratureAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputName("researchFindings")
                .build();
        HypothesisAgent hypothesisAgent = AgenticServices.agentBuilder(HypothesisAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputName("hypothesis")
                .build();
        CriticAgent criticAgent = AgenticServices.agentBuilder(CriticAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputName("critique")
                .build();
        ValidationAgent validationAgent = AgenticServices.agentBuilder(ValidationAgent.class)
                .chatModel(baseModel())
                .tools(arxivCrawler)
                .outputName("hypothesis")
                .build();

        ResearchAgent researcher = AgenticServices.p2pBuilder(ResearchAgent.class)
                .subAgents(literatureAgent, hypothesisAgent, criticAgent, validationAgent)
                .outputName("hypothesis")
                .maxAgentsInvocations(10)
                .build();

        System.out.println(researcher.research("black holes"));
    }
}
