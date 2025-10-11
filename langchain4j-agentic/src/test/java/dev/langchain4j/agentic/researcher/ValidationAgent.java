package dev.langchain4j.agentic.researcher;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ValidationAgent {

    @SystemMessage("Validate the provided hypothesis on the given topic based on the critique provided.")
    @UserMessage("""
            You are a validation agent.
            Your task is to validate the hypothesis provided by the user in relation to the specified topic based on the critique provided.
            Validate the provided hypothesis, either confirming it or reformulating a different hypothesis based on the critique.
            The topic is: {{topic}}
            The hypothesis is: {{hypothesis}}
            The critique is: {{critique}}
            """)
    @Agent("Validate a hypothesis based on a given topic and critique")
    String validateHypothesis(@V("topic") String topic, @V("hypothesis") String hypothesis, @V("critique") String critique);
}
