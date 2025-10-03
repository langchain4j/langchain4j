package dev.langchain4j.agentic.researcher;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface HypothesisAgent {

    @SystemMessage("Based on the research findings, formulate a clear and concise hypothesis related to the given topic.")
    @UserMessage("""
            You are a hypothesis formulation agent.
            Your task is to formulate a clear and concise hypothesis based on the research findings provided by the user.
            The topic is: {{topic}}
            The research findings are: {{researchFindings}}
            """)
    @Agent("Formulate hypothesis around a give topic based on research findings")
    String makeHypothesis(@V("topic") String topic, @V("researchFindings") String researchFindings);
}
