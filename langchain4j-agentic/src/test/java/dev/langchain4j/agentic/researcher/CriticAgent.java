package dev.langchain4j.agentic.researcher;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CriticAgent {

    @SystemMessage("Critically evaluate the given hypothesis related to the specified topic. Provide constructive feedback and suggest improvements if necessary.")
    @UserMessage("""
            You are a critical evaluation agent.
            Your task is to critically evaluate the hypothesis provided by the user in relation to the specified topic.
            Provide constructive feedback and suggest improvements if necessary.
            If you need to, you can also perform additional research to validate or confute the hypothesis using the provided tool.
            The topic is: {{topic}}
            The hypothesis is: {{hypothesis}}
            """)
    @Agent("Critically evaluate a hypothesis related to a given topic")
    String criticHypothesis(@V("topic") String topic, @V("hypothesis") String hypothesis);
}
