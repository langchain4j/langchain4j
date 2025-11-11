package dev.langchain4j.agentic.patterns.p2p;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;
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

import static dev.langchain4j.agentic.patterns.p2p.P2PAgent.P2P_REQUEST_KEY;
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
    public void init(InitPlanningContext initPlanningContext) {
        this.agentActivators = initPlanningContext.subagents().stream().collect(toMap(AgentInstance::agentId, AgentActivator::new));
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        if (planningContext.agenticScope().hasState(P2P_REQUEST_KEY)) {
            String request = planningContext.agenticScope().readState(P2P_REQUEST_KEY, "");
            Collection<String> variableNames = this.agentActivators.values().stream()
                    .flatMap(agentActivator -> agentActivator.argumentNames().stream())
                    .distinct().toList();

            Map<String, String> vars = createVariablesExtractorAgent(chatModel).extractVariables(request, variableNames);
            LOG.info("Variables extracted from user's prompt: {}", vars);
            vars.forEach(planningContext.agenticScope()::writeState);
        }

        return nextCallAction(planningContext.agenticScope());
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        if (terminated(planningContext.agenticScope())) {
            return done();
        }

        AgentActivator lastExecutedAgent = agentActivators.get(planningContext.previousAgentInvocation().agentId());
        lastExecutedAgent.finishExecution();
        agentActivators.values().forEach(a -> a.onStateChanged(lastExecutedAgent.agent.outputKey()));

        return nextCallAction(planningContext.agenticScope());
    }

    private Action nextCallAction(AgenticScope agenticScope) {
        AgentInstance[] agentsToCall = agentActivators.values().stream()
                .filter(agentActivator -> agentActivator.canActivate(agenticScope))
                .peek(AgentActivator::startExecution)
                .map(AgentActivator::agent)
                .toArray(AgentInstance[]::new);
        invocationCounter += agentsToCall.length;
        return call(agentsToCall);
    }

    private boolean terminated(AgenticScope agenticScope) {
        return invocationCounter > maxAgentsInvocations || exitCondition.test(agenticScope, invocationCounter);
    }

    private static class AgentActivator {
        private final AgentInstance agent;
        private final List<String> argumentNames;

        private volatile boolean executing = false;
        private volatile boolean shouldExecute = true;

        AgentActivator(AgentInstance agent) {
            this.agent = agent;
            this.argumentNames = agent.arguments().stream().map(AgentArgument::name).toList();
        }

        private AgentInstance agent() {
            return agent;
        }

        private boolean canActivate(AgenticScope agenticScope) {
            return !executing && shouldExecute && argumentNames.stream().allMatch(agenticScope::hasState);
        }

        private void startExecution() {
            LOG.info("Starting agent: {}", agent.agentId());
            shouldExecute = false;
            executing = true;
        }

        private void finishExecution() {
            LOG.info("Stopping agent: {}", agent.agentId());
            executing = false;
        }

        private void onStateChanged(String state) {
            boolean inputChanged = argumentNames.contains(state);
            // if the input changed, mark the agent to be executed again
            shouldExecute = shouldExecute || inputChanged;
        }

        private List<String> argumentNames() {
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
