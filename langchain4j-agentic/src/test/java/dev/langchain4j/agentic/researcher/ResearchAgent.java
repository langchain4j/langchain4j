package dev.langchain4j.agentic.researcher;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

public interface ResearchAgent {

    @Agent("Conduct research on a given topic")
    String research(@V("topic") String topic);
}
