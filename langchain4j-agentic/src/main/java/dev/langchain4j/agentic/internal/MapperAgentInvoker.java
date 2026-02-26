package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.MissingArgumentException;
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

    public MapperAgentInvoker(AgentInvoker delegate, Object item, int instanceIndex) {
        super(delegate.method(), delegate);
        this.item = item;
        this.injectionKey = delegate.arguments().isEmpty()
                ? null
                : delegate.arguments().get(0).name();
        this.instanceName = delegate.name() + "_" + instanceIndex;
        this.instanceAgentId = delegate.agentId() + "_" + instanceIndex;
        this.instanceOutputKey =
                delegate.outputKey() != null && !delegate.outputKey().isBlank()
                        ? delegate.outputKey() + "_" + instanceIndex
                        : null;
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
