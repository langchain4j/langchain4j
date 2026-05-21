package dev.langchain4j.agentic.agent;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServices;
import java.util.Arrays;

public class UntypedAgentBuilder extends AgentBuilder<UntypedAgent, UntypedAgentBuilder> {

    public Class<?> returnType = String.class; // default return type for untyped agents

    public UntypedAgentBuilder() {
        super(UntypedAgent.class);
    }

    public UntypedAgentBuilder returnType(Class<?> returnType) {
        this.returnType = returnType;
        return this;
    }

    public UntypedAgentBuilder inputs(AgentArgument... inputs) {
        this.arguments = Arrays.asList(inputs);
        return this;
    }

    public UntypedAgentBuilder inputKey(Class<?> type, String name) {
        return inputs(new AgentArgument(type, name));
    }

    public UntypedAgentBuilder inputKeys(Class<?> type1, String name1, Class<?> type2, String name2) {
        return inputs(new AgentArgument(type1, name1), new AgentArgument(type2, name2));
    }

    public UntypedAgentBuilder inputKeys(Class<?> type1, String name1, Class<?> type2, String name2,
                                         Class<?> type3, String name3) {
        return inputs(new AgentArgument(type1, name1), new AgentArgument(type2, name2),
                new AgentArgument(type3, name3));
    }

    public UntypedAgentBuilder inputKeys(Class<?> type1, String name1, Class<?> type2, String name2,
                                         Class<?> type3, String name3, Class<?> type4, String name4) {
        return inputs(new AgentArgument(type1, name1), new AgentArgument(type2, name2),
                new AgentArgument(type3, name3), new AgentArgument(type4, name4));
    }

    @Override
    protected void build(DefaultAgenticScope agenticScope, AiServiceContext context, AiServices<UntypedAgent> aiServices) {
        context.returnType = returnType;
    }
}
