package dev.langchain4j.agentic;

import dev.langchain4j.agentic.cognisphere.CognisphereAccess;
import dev.langchain4j.agentic.cognisphere.ResultWithCognisphere;

import java.util.Map;

public interface UntypedAgent extends CognisphereAccess {
    @Agent
    Object invoke(Map<String, Object> input);

    ResultWithCognisphere<String> invokeWithCognisphere(Map<String, Object> input);
}
