package dev.langchain4j.agentic.patterns.htn;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LlmDecompositionStrategy implements DecompositionStrategy {

    public record TaskStep(String agentName, String subtaskName, String subtaskDescription) {
        boolean isPrimitive() {
            return agentName != null && !agentName.isBlank();
        }
    }

    public record TaskPlan(List<TaskStep> steps) {}

    interface DecomposerAgent {
        @SystemMessage("{{instructions}}")
        @UserMessage("Context:\n{{context}}\n\nAvailable agents:\n{{agents}}")
        TaskPlan plan(@V("instructions") String instructions,
                      @V("context") String context,
                      @V("agents") String agents);
    }

    private static final String FLAT_INSTRUCTIONS = """
            You are a planning assistant. Given a context description and a set of available agents, \
            decide which agents to invoke and in what order to best address the requirements. \
            Return only direct agent invocations by setting agentName to the agent's name. \
            Leave subtaskName and subtaskDescription empty. \
            Select only agents that are relevant to the task.""";

    private static final String RECURSIVE_INSTRUCTIONS = """
            You are a planning assistant. Given a context description and a set of available agents, \
            decompose the task into steps. Each step is either: \
            a direct agent invocation (set agentName to the agent's name, leave subtaskName and subtaskDescription empty), \
            or a subtask for further decomposition (set subtaskName and subtaskDescription, leave agentName empty). \
            Use subtask objects sparingly - only when no single agent can handle the work. \
            Select only agents that are relevant to the task.""";

    private final ChatModel chatModel;
    private final String contextKey;
    private final Predicate<AgentInstance> candidateFilter;
    private final int maxDepth;
    private final int currentDepth;
    private final DecomposerAgent decomposerAgent;

    public LlmDecompositionStrategy(ChatModel chatModel, String contextKey, Class<?>... candidateAgentTypes) {
        this(chatModel, contextKey, 1, candidateAgentTypes);
    }

    public LlmDecompositionStrategy(ChatModel chatModel, String contextKey, int maxDepth, Class<?>... candidateAgentTypes) {
        this.chatModel = chatModel;
        this.contextKey = contextKey;
        this.maxDepth = maxDepth;
        this.currentDepth = 0;
        this.decomposerAgent = buildDecomposerAgent(chatModel);
        if (candidateAgentTypes.length == 0) {
            this.candidateFilter = a -> true;
        } else {
            Set<Class<?>> types = Set.of(candidateAgentTypes);
            this.candidateFilter = a -> types.contains(a.type());
        }
    }

    private LlmDecompositionStrategy(ChatModel chatModel, String contextKey, int maxDepth,
                                      int currentDepth, Predicate<AgentInstance> candidateFilter) {
        this.chatModel = chatModel;
        this.contextKey = contextKey;
        this.maxDepth = maxDepth;
        this.currentDepth = currentDepth;
        this.candidateFilter = candidateFilter;
        this.decomposerAgent = buildDecomposerAgent(chatModel);
    }

    private static DecomposerAgent buildDecomposerAgent(ChatModel chatModel) {
        return AiServices.builder(DecomposerAgent.class)
                .chatModel(chatModel)
                .build();
    }

    @Override
    public List<TaskNode> decompose(AgenticScope scope, Map<Class<?>, AgentInstance> agentsByType) {
        String context = scope.readState(contextKey, "");
        if (context.isBlank()) {
            return List.of();
        }

        String catalog = agentsByType.values().stream()
                .filter(candidateFilter)
                .map(a -> a.name() + ": " + a.description())
                .collect(Collectors.joining("\n"));

        if (catalog.isBlank()) {
            return List.of();
        }

        boolean canDecompose = currentDepth < maxDepth - 1;
        String instructions = canDecompose ? RECURSIVE_INSTRUCTIONS : FLAT_INSTRUCTIONS;

        TaskPlan taskPlan = decomposerAgent.plan(instructions, context, catalog);

        if (taskPlan == null || taskPlan.steps() == null) {
            return List.of();
        }

        List<TaskNode> result = new ArrayList<>();
        for (TaskStep step : taskPlan.steps()) {
            if (step.isPrimitive()) {
                agentsByType.values().stream()
                        .filter(candidateFilter)
                        .filter(a -> a.name().equals(step.agentName()))
                        .findFirst()
                        .ifPresent(a -> result.add(TaskNode.primitive(a)));
            } else if (step.subtaskName() != null && !step.subtaskName().isBlank()) {
                String subKey = contextKey + "/" + step.subtaskName();
                scope.writeState(subKey, step.subtaskDescription());
                result.add(TaskNode.compound(step.subtaskName(),
                        new LlmDecompositionStrategy(chatModel, subKey, maxDepth,
                                currentDepth + 1, candidateFilter)));
            }
        }
        return result;
    }
}
