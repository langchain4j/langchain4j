package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.workflow.ConditionalAgent;
import dev.langchain4j.agentic.workflow.ConditionalAgentInstance;
import java.util.List;

public class DefaultConditionalAgentInstance extends AbstractAgentInstance implements ConditionalAgentInstance {

    private final ConditionalPlanner planner;

    public DefaultConditionalAgentInstance(AgentInstance delegate, ConditionalPlanner planner) {
        super(delegate);
        this.planner = planner;
    }

    @Override
    public List<ConditionalAgent> conditionalSubagents() {
        return planner.conditionalSubagents();
    }
}
