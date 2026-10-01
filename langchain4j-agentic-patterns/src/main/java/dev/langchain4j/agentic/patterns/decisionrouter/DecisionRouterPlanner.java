package dev.langchain4j.agentic.patterns.decisionrouter;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A router planner that lets a {@link DecisionModel} choose which subagents handle a request.
 * <p>
 * Each subagent is an option of a single choice question, described by its name and description, and the input of
 * the question is made of the arguments of the router agent itself, read from the {@link AgenticScope}.
 * <p>
 * Without an activation threshold, the planner invokes only the most probable subagent and returns its output.
 * With an activation threshold, it invokes in parallel all the subagents whose probability is at least the threshold
 * and returns a map from the name of each invoked subagent to its output. Since the probabilities of the options
 * of a choice question sum to 1, a threshold {@code t} activates at most {@code 1/t} subagents, and when no subagent
 * reaches the threshold none is invoked and the result is an empty map.
 */
@Experimental
public class DecisionRouterPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionRouterPlanner.class);

    static final String QUESTION_NAME = "decision-agent";
    static final String QUESTION_TEXT = "Which agent is best suited to handle this request?";

    private final DecisionModel decisionModel;
    private final Double activationThreshold;

    private List<String> inputKeys;
    private ChoiceQuestion question;
    private final Map<String, AgentInstance> routes = new LinkedHashMap<>();
    private final Map<String, Object> outputs = new LinkedHashMap<>();

    /**
     * Creates a router invoking only the subagent that the decision model considers the most probable.
     */
    public DecisionRouterPlanner(DecisionModel decisionModel) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        this.activationThreshold = null;
    }

    /**
     * Creates a router invoking in parallel all the subagents whose probability is at least the given threshold.
     * The decision model must report the probabilities of the options.
     */
    public DecisionRouterPlanner(DecisionModel decisionModel, double activationThreshold) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        this.activationThreshold = ensureBetween(activationThreshold, 0.0, 1.0, "activationThreshold");
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        // framework-provided arguments, like @MemoryId, are not part of the request
        this.inputKeys = initPlanningContext.plannerAgent().arguments().stream()
                .map(AgentArgument::name)
                .filter(name -> !name.startsWith("@"))
                .toList();
        if (inputKeys.isEmpty()) {
            throw new IllegalArgumentException("DecisionRouterPlanner requires a typed agent interface whose method "
                    + "arguments are the input of the routing decision");
        }

        ChoiceQuestion.Builder questionBuilder = ChoiceQuestion.builder().text(QUESTION_TEXT);
        for (AgentInstance agent : initPlanningContext.subagents()) {
            if (routes.put(agent.name(), agent) != null) {
                throw new IllegalArgumentException("DecisionRouterPlanner requires subagents with distinct names, "
                        + "but more than one is named '" + agent.name() + "'");
            }
            if (isNullOrBlank(agent.description())) {
                questionBuilder.option(agent.name());
            } else {
                questionBuilder.option(agent.name(), agent.description());
            }
        }
        this.question = questionBuilder.build();
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        ChoiceAnswer answer = decide(planningContext.agenticScope());

        if (activationThreshold == null) {
            LOG.info("Routing to agent '{}'", answer.value());
            return call(routes.get(answer.value()));
        }

        List<AgentInstance> activated = routes.values().stream()
                .filter(agent -> answer.probabilityOf(agent.name()) >= activationThreshold)
                .toList();
        LOG.info(
                "Activating agents {} with probabilities {}",
                activated.stream().map(AgentInstance::name).toList(),
                answer.probabilities());
        return activated.isEmpty() ? done(outputs) : call(activated);
    }

    private ChoiceAnswer decide(AgenticScope agenticScope) {
        Map<String, Object> input = new LinkedHashMap<>();
        inputKeys.forEach(key -> input.put(key, agenticScope.readState(key)));

        return decisionModel
                .decide(DecisionRequest.builder()
                        .input(input)
                        .question(QUESTION_NAME, question)
                        .build())
                .choice(QUESTION_NAME);
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgentInvocation invocation = planningContext.previousAgentInvocation();
        if (activationThreshold == null) {
            return done(invocation.output());
        }
        // the loop resumes only when all the activated agents completed, so the last done action carries all outputs
        outputs.put(invocation.agentName(), invocation.output());
        return done(outputs);
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.ROUTER;
    }
}
