package dev.langchain4j.agentic.p2p;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public class P2PPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(P2PPlanner.class);

    private final ChatModel chatModel;
    private final int maxAgentsInvocations;
    private final BiPredicate<AgenticScope, Integer> exitCondition;

    private int invocationCounter = 0;

    private Map<String, AgentActivator> agentActivators;

    public P2PPlanner() {
        this(10);
    }

    public P2PPlanner(int maxAgentsInvocations) {
        this(maxAgentsInvocations, (scope, invocationsCounter) -> false);
    }

    public P2PPlanner(Predicate<AgenticScope> exitCondition) {
        this(10, exitCondition);
    }

    public P2PPlanner(int maxAgentsInvocations, Predicate<AgenticScope> exitCondition) {
        this(null, maxAgentsInvocations, exitCondition);
    }

    public P2PPlanner(int maxAgentsInvocations, BiPredicate<AgenticScope, Integer> exitCondition) {
        this(null, maxAgentsInvocations, exitCondition);
    }

    public P2PPlanner(ChatModel chatModel, int maxAgentsInvocations, Predicate<AgenticScope> exitCondition) {
        this(chatModel, maxAgentsInvocations, (scope, invocationsCounter) -> exitCondition.test(scope));
    }

    public P2PPlanner(ChatModel chatModel, int maxAgentsInvocations, BiPredicate<AgenticScope, Integer> exitCondition) {
        this.chatModel = chatModel;
        this.exitCondition = exitCondition;
        this.maxAgentsInvocations = maxAgentsInvocations;
    }

    @Override
    public void init(AgenticScope agenticScope, AgentInstance plannerAgent, List<AgentInstance> subagents) {
        this.agentActivators = subagents.stream().collect(toMap(AgentInstance::uniqueName, AgentActivator::new));
    }

    @Override
    public Action firstAction(AgenticScope agenticScope) {
        if (agenticScope.hasState("p2pRequest")) {
            String request = agenticScope.readState("p2pRequest", "");
            Collection<String> variableNames = this.agentActivators.values().stream()
                    .map(AgentActivator::argumentNames)
                    .flatMap(Stream::of).distinct().toList();

            Map<String, String> vars = createVariablesExtractorAgent(chatModel).extractVariables(request, variableNames);
            LOG.info("Variables extracted from user's prompt: {}", vars);
            vars.forEach(agenticScope::writeState);
        }

        return nextCallAction(agenticScope);
    }

    @Override
    public Action nextAction(AgenticScope agenticScope, AgentExecution lastAgentExecution) {
        if (terminated(agenticScope)) {
            return Action.done();
        }

        AgentActivator lastExecutedAgent = agentActivators.get(lastAgentExecution.agentSpec().uniqueName());
        lastExecutedAgent.finishExecution();
        agentActivators.values().forEach(a -> a.onStateChanged(lastExecutedAgent.agent.outputKey()));

        return nextCallAction(agenticScope);
    }

    private Action nextCallAction(AgenticScope agenticScope) {
        AgentInstance[] agentsToCall = agentActivators.values().stream()
                .filter(agentActivator -> agentActivator.canActivate(agenticScope))
                .peek(AgentActivator::startExecution)
                .map(AgentActivator::agent)
                .toArray(AgentInstance[]::new);
        invocationCounter += agentsToCall.length;
        return Action.call(agentsToCall);
    }

    private boolean terminated(AgenticScope agenticScope) {
        return invocationCounter > maxAgentsInvocations || exitCondition.test(agenticScope, invocationCounter);
    }

    private class AgentActivator {
        private final AgentInstance agent;
        private final String[] argumentNames;

        private volatile boolean executing = false;
        private volatile boolean shouldExecute = true;

        AgentActivator(AgentInstance agent) {
            this.agent = agent;
            this.argumentNames = agent.argumentNames();
        }

        private AgentInstance agent() {
            return agent;
        }

        private boolean canActivate(AgenticScope agenticScope) {
            return !executing && shouldExecute && Stream.of(argumentNames).allMatch(agenticScope::hasState);
        }

        private void startExecution() {
            LOG.info("Starting agent: {}", agent.uniqueName());
            shouldExecute = false;
            executing = true;
        }

        private void finishExecution() {
            LOG.info("Stopping agent: {}", agent.uniqueName());
            executing = false;
        }

        private void onStateChanged(String state) {
            boolean inputChanged = Stream.of(argumentNames).anyMatch(argName -> argName.equals(state));
            // if the input changed, mark the agent to be executed again
            shouldExecute = shouldExecute || inputChanged;
        }

        private String[] argumentNames() {
            return argumentNames;
        }
    }

    private static VariablesExtractorAgent createVariablesExtractorAgent(ChatModel chatModel) {
        if (chatModel == null) {
            throw new IllegalArgumentException("ChatModel must be provided for P2PAgent to extract variables from user's prompt.");
        }
        return AiServices.builder(VariablesExtractorAgent.class).chatModel(chatModel).build();
    }
}
