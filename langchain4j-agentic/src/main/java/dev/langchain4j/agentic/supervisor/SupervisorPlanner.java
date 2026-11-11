package dev.langchain4j.agentic.supervisor;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.stream.Collectors.toMap;

import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.ChatMemoryAccessProvider;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupervisorPlanner implements Planner, ChatMemoryAccessProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SupervisorPlanner.class);
    public static final String SUPERVISOR_CONTEXT_KEY = "supervisorContext";
    public static final String SUPERVISOR_CONTEXT_PREFIX = "Use the following supervisor context to better understand "
            + "constraints, policies or preferences when creating the plan ";

    /** Sent to the model in place of the description of a subagent that has none, so that it knows it is missing. */
    static final String MISSING_DESCRIPTION =
            "no description provided, infer the purpose of this agent from its id and arguments";

    private final ChatModel chatModel;
    private final Context.ContextSummarizer contextSummarizer;

    private final ChatMemoryProvider chatMemoryProvider;

    private final int maxAgentsInvocations;
    private int loopCount = 0;
    private String lastResponse = "";

    private ResponseAgent responseAgent;

    private final SupervisorContextStrategy contextStrategy;
    private final SupervisorResponseStrategy responseStrategy;

    private final Function<AgenticScope, String> requestGenerator;

    private final String outputKey;

    private final Function<AgenticScope, Object> output;

    private Map<String, AgentInstance> agents;
    private List<AgentInstance> subagents;

    // Shared by the planners of all the invocations of the same supervisor, so that the warning about the subagents
    // without a description is logged once
    private final AtomicBoolean subagentsDescriptionsChecked;

    // The subagents left out of this invocation because their description cannot be read: the planner is created
    // for each invocation of the supervisor, so the next one tries them again
    private final Set<String> unavailableSubagents = new HashSet<>();
    private String supervisorName;

    private String request;

    /**
     * Creates a supervisor planner reusing a pre-built {@link Context.ContextSummarizer}, so that
     * planners created for separate invocations can share the same summarizer AI service.
     *
     * @param subagentsDescriptionsChecked whether the subagents have already been checked for a missing description,
     *        shared by the planners of all the invocations of the same supervisor
     * @throws IllegalConfigurationException if {@code contextStrategy} requires summarization (any
     *         strategy other than {@link SupervisorContextStrategy#CHAT_MEMORY}) and
     *         {@code contextSummarizer} is {@code null}
     */
    SupervisorPlanner(
            ChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider,
            int maxAgentsInvocations,
            SupervisorContextStrategy contextStrategy,
            SupervisorResponseStrategy responseStrategy,
            Function<AgenticScope, String> requestGenerator,
            String outputKey,
            Function<AgenticScope, Object> output,
            Context.ContextSummarizer contextSummarizer,
            AtomicBoolean subagentsDescriptionsChecked) {
        if (contextStrategy != SupervisorContextStrategy.CHAT_MEMORY && contextSummarizer == null) {
            throw new IllegalConfigurationException(
                    "A ContextSummarizer is required for the " + contextStrategy + " context strategy.");
        }
        this.chatModel = chatModel;
        this.contextSummarizer = contextSummarizer;
        this.chatMemoryProvider = chatMemoryProvider;
        this.maxAgentsInvocations = maxAgentsInvocations;
        this.contextStrategy = contextStrategy;
        this.responseStrategy = responseStrategy;
        this.requestGenerator = requestGenerator;
        this.outputKey = outputKey;
        this.output = output;
        this.subagentsDescriptionsChecked = subagentsDescriptionsChecked;
    }

    @Override
    public void init(final InitPlanningContext initPlanningContext) {
        this.supervisorName = initPlanningContext.plannerAgent().name();
        warnAboutSubagentsWithoutDescription(initPlanningContext);
        this.agents =
                initPlanningContext.subagents().stream().collect(toMap(AgentInstance::agentId, Function.identity()));
        this.subagents = initPlanningContext.subagents();

        this.request = requestGenerator != null
                ? requestGenerator.apply(initPlanningContext.agenticScope())
                : initPlanningContext.agenticScope().readState("request", "");
        if (responseStrategy == SupervisorResponseStrategy.SCORED) {
            this.responseAgent =
                    AiServices.builder(ResponseAgent.class).chatModel(chatModel).build();
        }
    }

    /**
     * The supervisor sends the description of each subagent to its model to decide which one to invoke, so a
     * subagent without a description cannot be chosen meaningfully: the model receives {@link #MISSING_DESCRIPTION}
     * in its place and a warning naming those subagents is logged once per supervisor, at its first invocation, for
     * the subagents whose description can be read at that time. Reading the description of a remote agent, like an
     * A2A one without an explicit description, may fetch it from the remote server at this point.
     */
    private void warnAboutSubagentsWithoutDescription(InitPlanningContext initPlanningContext) {
        if (subagentsDescriptionsChecked.get()) {
            return;
        }
        List<String> subagentsWithoutDescription = initPlanningContext.subagents().stream()
                .filter(this::isAvailable)
                .filter(agent -> isNullOrBlank(agent.description()))
                .map(AgentInstance::name)
                .toList();
        if (subagentsDescriptionsChecked.compareAndSet(false, true) && !subagentsWithoutDescription.isEmpty()) {
            LOG.warn(
                    "The subagents {} of the supervisor '{}' have no description. The supervisor relies on the"
                            + " descriptions of its subagents to decide which one to invoke, so provide one for each"
                            + " of them: until then its model is told that no description was provided.",
                    subagentsWithoutDescription,
                    supervisorName);
        }
    }

    /**
     * A subagent that is unavailable, like an A2A or MCP agent whose description cannot be read because its server
     * is down, is left out of the agents offered to the model for the rest of this invocation, with a warning, so
     * that a remote agent that is down only affects the invocations that actually reach it. The planner is created
     * for each invocation of the supervisor, so the next one tries that subagent again. Only an
     * {@link AgentUnavailableException} means that: any other failure of the description, like a configuration
     * error of an MCP agent whose tool does not exist on its server, propagates and fails the invocation, instead of
     * being hidden behind a warning at every invocation.
     */
    private boolean isAvailable(AgentInstance agent) {
        if (unavailableSubagents.contains(agent.agentId())) {
            return false;
        }
        try {
            agent.description();
            return true;
        } catch (AgentUnavailableException e) {
            unavailableSubagents.add(agent.agentId());
            LOG.warn(
                    "The subagent '{}' of the supervisor '{}' is left out of this invocation, because it is"
                            + " unavailable and its description cannot be read",
                    agent.agentId(),
                    supervisorName,
                    e);
            return false;
        }
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        // HITL / SuspendedResponse writes an incomplete deferred value into scope, then notifies the
        // planner via onSubagentInvoked. Sequential planners must still advance their cursor here;
        // the supervisor must not call the LLM with lastResponse="<pending:…>" — that burns a turn,
        // pollutes chat memory, and commonly re-selects the same ask-user agent after resume.
        if (hasIncompleteSuspendedResponse(planningContext.agenticScope())) {
            return suspend();
        }

        dev.langchain4j.agentic.scope.AgentInvocation previousInvocation = planningContext.previousAgentInvocation();
        if (previousInvocation != null && !previousInvocation.skipped()) {
            lastResponse = previousInvocation.output() == null
                    ? ""
                    : previousInvocation.output().toString();
        }

        if (loopCount++ >= maxAgentsInvocations) {
            return doneAction(planningContext.agenticScope(), lastResponse, null);
        }
        // a skipped agent produced no response, so the model is told why instead
        String plannerResponse = previousInvocation != null && previousInvocation.skipped()
                ? "agent '" + previousInvocation.agentName() + "' was skipped: " + previousInvocation.skipReason()
                : lastResponse;
        return nextSubagent(planningContext, plannerResponse, lastResponse);
    }

    private static boolean hasIncompleteSuspendedResponse(AgenticScope agenticScope) {
        return agenticScope.state().values().stream()
                .anyMatch(v -> v instanceof SuspendedResponse<?> sr && !sr.isDone());
    }

    private static String toCard(AgentInstance agent) {
        List<String> agentArguments = agent.arguments().stream()
                .filter(a -> !a.name().equals("@MemoryId"))
                .map(SupervisorPlanner::argumentDescription)
                .toList();
        String description = agent.description();
        if (isNullOrBlank(description)) {
            description = MISSING_DESCRIPTION;
        }
        return "{'" + agent.agentId() + "', '" + description + "', " + agentArguments + "}";
    }

    private static String argumentDescription(AgentArgument arg) {
        String description = arg.description();
        if (description != null && !description.isBlank()) {
            return argumentDescription(arg.type(), arg.name()) + " - " + description;
        }
        return argumentDescription(arg.type(), arg.name());
    }

    /**
     * Describes one agent argument the way the supervisor's planning prompt expects it, for example
     * {@code fields: List<String>} or {@code task: {title: String, priority: int}}.
     *
     * <p>The declared {@link Type} is used instead of its erased {@link Class}, because the planner
     * has to know the shape of an argument to invoke the agent with usable values. An erased
     * {@code List} tells it nothing, and it ends up sending a comma separated string where a list
     * was expected.
     */
    static String argumentDescription(Type type, String name) {
        if (name == null) {
            return "";
        }

        return name + ": " + typeDescription(type);
    }

    private static String typeDescription(Type type) {
        if (type instanceof GenericArrayType genericArrayType) {
            return typeDescription(genericArrayType.getGenericComponentType()) + "[]";
        }

        if (type instanceof ParameterizedType parameterizedType) {
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            if (Collection.class.isAssignableFrom(rawType) || Map.class.isAssignableFrom(rawType)) {
                return typeName(parameterizedType);
            }
            return objectDescription(rawType);
        }

        if (!(type instanceof Class<?> clazz)) {
            return type.getTypeName();
        }

        if (clazz.isArray()) {
            return typeDescription(clazz.getComponentType()) + "[]";
        }

        if (isSimpleType(clazz)
                || clazz == Object.class
                || Collection.class.isAssignableFrom(clazz)
                || Map.class.isAssignableFrom(clazz)) {
            return clazz.getSimpleName();
        }

        return objectDescription(clazz);
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || type == String.class
                || type == Boolean.class
                || Number.class.isAssignableFrom(type);
    }

    private static String objectDescription(Class<?> type) {
        String fieldsDescription = type.isRecord()
                ? Stream.of(type.getDeclaredConstructors()[0].getParameters())
                        .map(p -> argumentDescription(p.getParameterizedType(), ParameterNameResolver.name(p)))
                        .collect(Collectors.joining(", "))
                : fieldsIncludingInherited(type).stream()
                        .map(f -> argumentDescription(f.getGenericType(), f.getName()))
                        .collect(Collectors.joining(", "));

        return "{" + fieldsDescription + "}";
    }

    private static String typeName(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz.isArray() ? typeName(clazz.getComponentType()) + "[]" : clazz.getSimpleName();
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return typeName(genericArrayType.getGenericComponentType()) + "[]";
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return typeName(parameterizedType.getRawType()) + "<"
                    + Stream.of(parameterizedType.getActualTypeArguments())
                            .map(SupervisorPlanner::typeName)
                            .collect(Collectors.joining(", "))
                    + ">";
        }
        return type.getTypeName();
    }

    private static List<Field> fieldsIncludingInherited(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        collectFields(type, fields);
        return List.copyOf(fields);
    }

    /**
     * Collects the declared fields of {@code type} followed by those of its
     * superclasses (up to, excluding, {@code Object}), so the supervisor's
     * request context describes the whole state of an output POJO that extends
     * a base class. A field redeclared in a subclass shadows the inherited one.
     */
    private static void collectFields(Class<?> type, List<Field> fields) {
        if (type == null || type == Object.class) {
            return;
        }
        collectFields(type.getSuperclass(), fields);
        for (Field field : type.getDeclaredFields()) {
            fields.removeIf(inherited -> inherited.getName().equals(field.getName()));
            fields.add(field);
        }
    }

    private Action nextSubagent(PlanningContext planningContext, String plannerResponse, String lastResponse) {
        AgenticScope agenticScope = planningContext.agenticScope();
        // only the agents that would run now are offered to the model
        String agentsList = subagents.stream()
                .filter(planningContext::isActive)
                .filter(this::isAvailable)
                .map(SupervisorPlanner::toCard)
                .collect(Collectors.joining(", "));
        String supervisorContext = agenticScope.hasState(SUPERVISOR_CONTEXT_KEY)
                ? SUPERVISOR_CONTEXT_PREFIX + "'" + agenticScope.readState(SUPERVISOR_CONTEXT_KEY, "") + "'."
                : "";

        AgentInvocation agentInvocation = withAgenticScope(
                agenticScope,
                () -> planner(agenticScope)
                        .plan(agenticScope.memoryId(), agentsList, request, plannerResponse, supervisorContext));
        LOG.info("Agent Invocation: {}", agentInvocation);

        if (agentInvocation.getAgentName() == null) {
            throw new IllegalStateException("No agent name in the planning reply");
        }

        if (agentInvocation.getAgentName().equalsIgnoreCase("done")) {
            return doneAction(agenticScope, lastResponse, agentInvocation);
        }

        AgentInstance agent = findAgentByName(agentInvocation.getAgentName());

        Map<String, Object> arguments = Objects.requireNonNullElse(agentInvocation.getArguments(), Map.of());
        arguments.entrySet().stream()
                .filter(entry -> writeArgumentToScope(agenticScope, agent, entry.getKey(), entry.getValue()))
                .forEach(entry -> agenticScope.writeState(entry.getKey(), entry.getValue()));
        return call(agent);
    }

    private static <T> T withAgenticScope(AgenticScope agenticScope, Supplier<T> supplier) {
        LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, agenticScope));
        try {
            return supplier.get();
        } finally {
            LangChain4jManaged.removeCurrent();
        }
    }

    private AgentInstance findAgentByName(String agentName) {
        AgentInstance agent = agents.get(agentName);
        if (agent == null) {
            List<AgentInstance> candidateAgents = agents.values().stream()
                    .filter(a -> a.name().equals(agentName))
                    .toList();
            if (candidateAgents.size() == 1) {
                agent = candidateAgents.get(0);
            }
        }
        if (agent == null) {
            throw new IllegalStateException("No agent found with name: " + agentName);
        }
        return agent;
    }

    private boolean writeArgumentToScope(AgenticScope agenticScope, AgentInstance agent, String key, Object value) {
        if (agenticScope.hasState(key)) {
            Class<?> argType = agent.arguments().stream()
                    .filter(arg -> arg.name().equals(key))
                    .findFirst()
                    .map(AgentArgument::rawType)
                    .orElse(null);
            if (argType != null) {
                Object existingValue = agenticScope.readState(key);
                // avoid overwriting a structured state with an unstructured argument generated from supervisor's LLM
                // response
                return !argType.isAssignableFrom(existingValue.getClass())
                        || argType.isAssignableFrom(value.getClass());
            }
        }
        return true;
    }

    private Action doneAction(AgenticScope agenticScope, String lastResponse, AgentInvocation done) {
        Object result = result(agenticScope, request, lastResponse, done);
        if (outputKey != null) {
            agenticScope.writeState(outputKey, result);
        }
        return done(output != null ? output.apply(agenticScope) : result);
    }

    private PlannerAgent planner(AgenticScope agenticScope) {
        return ((DefaultAgenticScope) agenticScope).getOrCreateAgent(agentId(), this::buildPlannerAgent);
    }

    private Object result(AgenticScope agenticScope, String request, String lastResponse, AgentInvocation done) {
        if (done == null || done.getArguments() == null || done.getArguments().get("response") == null) {
            return lastResponse;
        }
        String doneResponse = done.getArguments().get("response").toString();

        return switch (responseStrategy) {
            case LAST -> lastResponse;
            case SUMMARY -> doneResponse;
            case SCORED -> {
                ResponseScore score = withAgenticScope(
                        agenticScope, () -> responseAgent.scoreResponses(request, lastResponse, doneResponse));
                LOG.info("Response scores: {}", score);
                yield score.getScore2() > score.getScore1() ? doneResponse : lastResponse;
            }
        };
    }

    private PlannerAgent buildPlannerAgent(AgenticScope agenticScope) {
        var builder = AiServices.builder(PlannerAgent.class).chatModel(chatModel);
        configureMemoryAndContext(agenticScope, builder);
        return builder.build();
    }

    private void configureMemoryAndContext(AgenticScope agenticScope, AiServices<PlannerAgent> builder) {
        if (chatMemoryProvider != null) {
            builder.chatMemoryProvider(chatMemoryProvider);
            if (contextStrategy != SupervisorContextStrategy.CHAT_MEMORY) {
                builder.chatRequestTransformer(Context.Summarizer.withSummarizer(agenticScope, contextSummarizer));
            }
        } else {
            switch (contextStrategy) {
                case CHAT_MEMORY:
                    builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20));
                    break;
                case SUMMARIZATION:
                    builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(2))
                            .chatRequestTransformer(Context.Summarizer.withSummarizer(agenticScope, contextSummarizer));
                    break;
                case CHAT_MEMORY_AND_SUMMARIZATION:
                    builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                            .chatRequestTransformer(Context.Summarizer.withSummarizer(agenticScope, contextSummarizer));
                    break;
            }
        }
    }

    private String agentId() {
        return outputKey + "@Supervisor";
    }

    @Override
    public Map<String, Object> executionState() {
        return Map.of("loopCount", loopCount);
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        Object savedLoopCount = state.get("loopCount");
        if (savedLoopCount instanceof Number n) {
            this.loopCount = n.intValue();
        }
    }

    @Override
    public ChatMemoryAccess chatMemoryAccess(AgenticScope agenticScope) {
        return planner(agenticScope);
    }
}
