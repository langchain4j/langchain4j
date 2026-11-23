package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Map;

/**
 * Wraps an existing {@link AgentInvoker} to inject a specific item from a collection
 * into the agent's invocation arguments. Each instance represents one element of a
 * parallel mapper execution, with a unique name, agentId, and outputKey.
 */
public class MapperAgentInvoker extends AbstractAgentInvoker {

    private final Object item;
    private final String injectionKey;
    private final String instanceName;
    private final String instanceAgentId;
    private final String instanceOutputKey;

    public MapperAgentInvoker(AgentInvoker delegate, Object item, int instanceIndex, String outputKeyPrefix) {
        super(delegate.method(), delegate);
        this.item = item;
        this.injectionKey = itemArgumentName(delegate);
        this.instanceName = delegate.name() + "_" + instanceIndex;
        this.instanceAgentId = delegate.agentId() + "_" + instanceIndex;
        this.instanceOutputKey = outputKeyPrefix + "_" + instanceIndex;
    }

    /**
     * The item is passed to the first argument of the subagent that is not provided by the framework, like its
     * {@link AgenticScope} or its memory id, whose names start with {@code @}, so that such an argument can come first.
     */
    private static String itemArgumentName(AgentInvoker delegate) {
        return delegate.arguments().stream()
                .map(AgentArgument::name)
                .filter(name -> !name.startsWith("@"))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String name() {
        return instanceName;
    }

    @Override
    public String agentId() {
        return instanceAgentId;
    }

    @Override
    public String outputKey() {
        return instanceOutputKey;
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException {
        if (injectionKey == null) {
            return AgentUtil.agentInvocationArguments(agenticScope, arguments());
        }
        return AgentUtil.agentInvocationArguments(agenticScope, arguments(), Map.of(injectionKey, item));
    }
}
