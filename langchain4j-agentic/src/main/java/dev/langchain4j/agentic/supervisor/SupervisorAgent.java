package dev.langchain4j.agentic.supervisor;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.cognisphere.CognisphereAccess;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;
import dev.langchain4j.service.V;

public interface SupervisorAgent extends CognisphereAccess {
    @Agent
    String invoke(@V("request") String request);

    ResultWithCognisphere<String> invokeWithCognisphere(@V("request") String request);
}
