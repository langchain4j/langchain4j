package dev.langchain4j.agentic.patterns.p2p.researcher;

import static dev.langchain4j.test.model.Models.baseModel;
import static dev.langchain4j.test.model.Models.plannerModel;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;

public class SupervisorResearcher {

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

        SupervisorAgent researcher = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel())
                .responseStrategy(SupervisorResponseStrategy.SCORED)
                .subAgents(literatureAgent, hypothesisAgent, criticAgent, validationAgent)
                .build();

        System.out.println(researcher.invoke("Formulate a new theory on black holes"));
    }
}
