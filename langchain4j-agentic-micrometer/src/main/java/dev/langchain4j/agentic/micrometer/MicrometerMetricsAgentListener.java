package dev.langchain4j.agentic.micrometer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentAiServiceEvent;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.BeforeAgentToolExecution;
import dev.langchain4j.agentic.observability.PlannerModelResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiAttributes;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiMetricName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiOperationName;
import dev.langchain4j.micrometer.metrics.conventions.OTelGenAiTokenType;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.spi.services.PublisherAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * An {@link AgentListener} that uses a Micrometer {@link MeterRegistry} to collect metrics about the agents of an
 * agentic system following the OpenTelemetry Semantic Conventions for Generative AI.
 * <p>
 * Registered on the root of an agentic system, it is inherited by all its subagents and records:
 * <ul>
 *     <li>{@code gen_ai.invoke_agent.duration}: the duration of each invocation of an agent, tagged with
 *     {@code gen_ai.agent.name} and with {@code gen_ai.operation.name} set to {@code invoke_agent}, or to
 *     {@code invoke_workflow} for an agent coordinating subagents, like a sequence or a supervisor. For an LLM-based
 *     agent it is the duration of the invocation of its AI Service, so for a streaming agent it ends when the stream
 *     completes, not when the stream is returned;</li>
 *     <li>{@code gen_ai.invoke_agent.inference_calls} and {@code gen_ai.invoke_agent.tool_calls}: the number of model
 *     calls, failed ones included, and of tool calls made by each invocation of an LLM-based agent, without the ones
 *     of other agents it calls;</li>
 *     <li>{@code gen_ai.execute_tool.duration}: the duration of each tool execution, tagged with
 *     {@code gen_ai.tool.name} and {@code gen_ai.agent.name};</li>
 *     <li>{@code langchain4j.agent.token.usage}: the input and the output tokens of the model calls made by each
 *     invocation of an agent itself, without the ones of other agents it calls, tagged with {@code gen_ai.token.type}:
 *     the calls of the AI Service of an LLM-based agent, and the calls that the planner of a supervisor or of another
 *     pattern makes to decide what to do, as reported to {@link #onPlannerModelResponse}. Not part of the conventions,
 *     which have no token metric per agent;</li>
 *     <li>{@code langchain4j.agent.invocations}: the number of invocations of each agent by each agent coordinating
 *     it, tagged with {@code gen_ai.agent.name} and {@code langchain4j.agent.parent.name}, to show for instance which
 *     subagents a supervisor calls. Not part of the conventions;</li>
 *     <li>{@code langchain4j.agent.invocations.active}: a {@link LongTaskTimer} of the invocations in progress, tagged
 *     like their durations without the outcome, giving how many are running and for how long, to spot an agent that
 *     never ends. An invocation is active until its duration is recorded. Not part of the conventions.</li>
 * </ul>
 * Durations and invocations carry an {@code outcome} tag set to {@code SUCCESS}, {@code ERROR}, {@code CANCELLED}
 * or {@code SUSPENDED}; durations also carry an {@code error.type} tag set to {@code none}, to the class name of the
 * exception that caused the failure, or to {@code _OTHER} for a failed tool execution whose exception is not known.
 * A tool the model asks for that was not offered to it is tagged {@code gen_ai.tool.name=_OTHER}, as the model can
 * invent any name.
 * Every meter carries the same set of tags, as some registries (e.g., Prometheus) require that; tags of your own can
 * be added to all of them with {@link Builder#tags(Function)}.
 * <p>
 * Agents are tagged with the name they are defined with: the items of a parallel mapper are all reported under the
 * name of its subagent, so the number of series does not grow with the number of items. When a human in the loop
 * suspends the agentic system, the agents waiting for it are recorded as {@code SUSPENDED}, and resuming the system
 * starts new invocations, so the time the human takes is never part of a duration. An asynchronous agent whose
 * output is not read runs detached from the root call: it is recorded when it completes, also after the root call
 * returned, and the duration of the workflow does not include it. An LLM-based agent returning a reactive stream is
 * recorded once per subscription, a workflow returning one when it returns it, and a {@code TokenStream} that is never
 * started stays active.
 * <p>
 * Histogram publishing and bucket boundaries are not configured by this listener.
 * Users can enable histograms and set bucket boundaries through their {@link MeterRegistry} configuration.
 */
@Experimental
public class MicrometerMetricsAgentListener implements AgentListener {

    private static final String AGENT_TOKEN_USAGE = "langchain4j.agent.token.usage";
    private static final String AGENT_INVOCATIONS = "langchain4j.agent.invocations";
    private static final String ACTIVE_AGENT_INVOCATIONS = "langchain4j.agent.invocations.active";
    private static final String PARENT_AGENT_NAME = "langchain4j.agent.parent.name";

    private static final String OUTCOME = "outcome";
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_ERROR = "ERROR";
    private static final String OUTCOME_CANCELLED = "CANCELLED";
    private static final String OUTCOME_SUSPENDED = "SUSPENDED";
    private static final String NO_ERROR = "none";
    private static final String OTHER_ERROR = "_OTHER";
    private static final String FUNCTION_TOOL = "function";
    private static final String OTHER_TOOL = "_OTHER";

    private static final Collection<PublisherAdapter> PUBLISHER_ADAPTERS = loadFactories(PublisherAdapter.class);

    private final Function<AgenticScope, Iterable<Tag>> tags;

    private final TaggedMeters<Timer> invocationDuration;
    private final TaggedMeters<DistributionSummary> inferenceCalls;
    private final TaggedMeters<DistributionSummary> toolCalls;
    private final TaggedMeters<Timer> toolDuration;
    private final TaggedMeters<DistributionSummary> tokenUsage;
    private final TaggedMeters<Counter> invocations;
    private final TaggedMeters<LongTaskTimer> activeInvocations;

    private final Map<InvocationKey, PendingInvocation> pendingInvocations = new ConcurrentHashMap<>();
    private final Map<UUID, AiServiceInvocation> aiServiceInvocations = new ConcurrentHashMap<>();
    private final ThreadLocal<PendingInvocation> startingInvocation = new ThreadLocal<>();

    /**
     * Creates a new {@link MicrometerMetricsAgentListener}.
     *
     * @param meterRegistry the {@link MeterRegistry} to register metrics with
     */
    public MicrometerMetricsAgentListener(MeterRegistry meterRegistry) {
        this(builder().meterRegistry(meterRegistry));
    }

    private MicrometerMetricsAgentListener(Builder builder) {
        MeterRegistry meterRegistry = ensureNotNull(builder.meterRegistry, "meterRegistry");
        this.tags = builder.tags;
        boolean caching = tags == null;
        invocationDuration = new TaggedMeters<>(
                caching,
                Timer.builder(OTelGenAiMetricName.INVOKE_AGENT_DURATION.value())
                        .description("Measures the duration of agent and workflow invocations")
                        .withRegistry(meterRegistry),
                OTelGenAiAttributes.OPERATION_NAME.value(),
                OTelGenAiAttributes.AGENT_NAME.value(),
                OUTCOME,
                OTelGenAiAttributes.ERROR_TYPE.value());
        inferenceCalls = new TaggedMeters<>(
                caching,
                DistributionSummary.builder(OTelGenAiMetricName.INVOKE_AGENT_INFERENCE_CALLS.value())
                        .description("Measures the number of model calls of agent invocations")
                        .baseUnit("{inference_call}")
                        .withRegistry(meterRegistry),
                OTelGenAiAttributes.OPERATION_NAME.value(),
                OTelGenAiAttributes.AGENT_NAME.value());
        toolCalls = new TaggedMeters<>(
                caching,
                DistributionSummary.builder(OTelGenAiMetricName.INVOKE_AGENT_TOOL_CALLS.value())
                        .description("Measures the number of tool calls of agent invocations")
                        .baseUnit("{tool_call}")
                        .withRegistry(meterRegistry),
                OTelGenAiAttributes.OPERATION_NAME.value(),
                OTelGenAiAttributes.AGENT_NAME.value());
        toolDuration = new TaggedMeters<>(
                caching,
                Timer.builder(OTelGenAiMetricName.EXECUTE_TOOL_DURATION.value())
                        .description("Measures the duration of tool executions")
                        .withRegistry(meterRegistry),
                OTelGenAiAttributes.TOOL_NAME.value(),
                OTelGenAiAttributes.TOOL_TYPE.value(),
                OTelGenAiAttributes.AGENT_NAME.value(),
                OUTCOME,
                OTelGenAiAttributes.ERROR_TYPE.value());
        tokenUsage = new TaggedMeters<>(
                caching,
                DistributionSummary.builder(AGENT_TOKEN_USAGE)
                        .description("Measures the token usage of agent invocations")
                        .baseUnit("tokens")
                        .withRegistry(meterRegistry),
                OTelGenAiAttributes.OPERATION_NAME.value(),
                OTelGenAiAttributes.AGENT_NAME.value(),
                OTelGenAiAttributes.TOKEN_TYPE.value());
        invocations = new TaggedMeters<>(
                caching,
                Counter.builder(AGENT_INVOCATIONS)
                        .description("Counts the invocations of agents by the agents coordinating them")
                        .withRegistry(meterRegistry),
                OTelGenAiAttributes.AGENT_NAME.value(),
                PARENT_AGENT_NAME,
                OUTCOME);
        activeInvocations = new TaggedMeters<>(
                caching,
                LongTaskTimer.builder(ACTIVE_AGENT_INVOCATIONS)
                        .description("Measures the agent and workflow invocations in progress")
                        .withRegistry(meterRegistry),
                OTelGenAiAttributes.OPERATION_NAME.value(),
                OTelGenAiAttributes.AGENT_NAME.value());
        if (caching) {
            List<TaggedMeters<?>> cachedMeters = List.of(
                    invocationDuration,
                    inferenceCalls,
                    toolCalls,
                    toolDuration,
                    tokenUsage,
                    invocations,
                    activeInvocations);
            meterRegistry.config().onMeterRemoved(meter -> cachedMeters.forEach(meters -> meters.evict(meter)));
        }
    }

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        InvocationKey key = invocationKey(agentRequest.agenticScope(), agentRequest.agentId());
        AgentInstance agent = agentRequest.agent();
        Tags customTags;
        LongTaskTimer.Sample active;
        try {
            customTags = customTags(agentRequest.agenticScope());
            active = startActive(agent, customTags);
        } catch (RuntimeException e) {
            open(new PendingInvocation(key, agent));
            throw e;
        }
        open(new PendingInvocation(key, agent, customTags, active, System.nanoTime()));
    }

    /**
     * Opens an invocation until its end callback or until its AI Service takes it over; one whose tags could not be
     * computed is opened too, so that it is not recorded, nor mistaken for another invocation, before it ends.
     */
    private void open(PendingInvocation pending) {
        pendingInvocations.put(pending.key, pending);
        startingInvocation.set(pending);
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        PendingInvocation pending =
                pendingInvocations.remove(invocationKey(agentResponse.agenticScope(), agentResponse.agentId()));
        startingInvocation.remove();
        if (pending == null || pending.skipped()) {
            return;
        }
        if (pending.agent.topology() == AgenticSystemTopology.AI_AGENT && returnsReactiveStream(pending.agent)) {
            pending.endActive();
            return;
        }
        recordInvocation(pending, OUTCOME_SUCCESS, NO_ERROR);
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        PendingInvocation pending = pendingInvocations.remove(
                invocationKey(agentInvocationError.agenticScope(), agentInvocationError.agentId()));
        startingInvocation.remove();
        if (pending == null) {
            countFailureBeforeInvocation(agentInvocationError);
        } else if (!pending.skipped()) {
            recordInvocation(pending, OUTCOME_ERROR, errorType(agentInvocationError.error()));
        }
    }

    /**
     * Counts the failure of an agent that failed before being invoked, like an MCP agent whose tool cannot be
     * resolved, under the agent invoking it: it has no duration, as it never started. An LLM-based agent is not
     * counted here, as its invocation was already recorded by its AI Service.
     */
    private void countFailureBeforeInvocation(AgentInvocationError agentInvocationError) {
        AgentInstance agent = agentInvocationError.agent();
        if (agent.topology() == AgenticSystemTopology.AI_AGENT) {
            return;
        }
        countInvocation(agent, customTags(agentInvocationError.agenticScope()), definitionName(agent), OUTCOME_ERROR);
    }

    /**
     * Records the agents waiting for the human in the loop as suspended: the workflow notifying the suspension, the
     * workflows containing it on the same thread, and an agent that suspended the system itself by throwing, whose
     * invocation is still open there. Each workflow containing the suspended step notifies it on the thread it runs on,
     * as the suspension reaches it, so the agents running on other threads, like a parallel branch or an asynchronous
     * agent, are recorded when they end, or when their own workflow notifies the suspension.
     */
    @Override
    public void onAgenticSystemSuspended(AgenticScope agenticScope) {
        Object memoryId = agenticScope.memoryId();
        Thread thread = Thread.currentThread();
        pendingInvocations.forEach((key, pending) -> {
            if (key.thread() == thread
                    && key.memoryId().equals(memoryId)
                    && pendingInvocations.remove(key, pending)
                    && !pending.skipped()) {
                recordInvocation(pending, OUTCOME_SUSPENDED, NO_ERROR);
            }
        });
    }

    /**
     * Adds the tokens of a model call made by the planner of a pattern, like a supervisor, to the invocation of that
     * pattern. These notifications are sent for the responses the planner receives, not for every call it makes, so
     * they are not counted as inference calls, which include the failed ones.
     */
    @Override
    public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
        PendingInvocation pending = pendingInvocations.get(
                invocationKey(plannerModelResponse.agenticScope(), plannerModelResponse.agentId()));
        if (pending != null && !pending.skipped()) {
            pending.addTokens(plannerModelResponse.tokenUsage());
        }
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {
        AiServiceInvocation invocation = aiServiceInvocations.get(
                beforeAgentToolExecution.toolExecution().invocationContext().invocationId());
        if (invocation != null) {
            invocation.toolStarted(beforeAgentToolExecution.toolExecution().request(), System.nanoTime());
        }
    }

    /**
     * Records a tool execution with the tags of the invocation of the AI Service executing it, which always starts
     * before its tools.
     */
    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
        ToolExecution toolExecution = afterAgentToolExecution.toolExecution();
        AiServiceInvocation invocation =
                aiServiceInvocations.get(toolExecution.invocationContext().invocationId());
        if (invocation == null) {
            return;
        }
        invocation.toolEnded(toolExecution.request());
        String toolName = invocation.toolName(toolExecution.request());
        String agentName = afterAgentToolExecution.agentInstance().name();
        List<String> tagValues = toolExecution.hasFailed()
                ? toolTagValues(toolName, agentName, OUTCOME_ERROR, OTHER_ERROR)
                : toolTagValues(toolName, agentName, OUTCOME_SUCCESS, NO_ERROR);
        toolDuration.withTags(invocation.customTags, tagValues).record(toolExecution.duration());
    }

    @Override
    public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
        AiServiceEvent event = agentAiServiceEvent.aiServiceEvent();
        UUID invocationId = event.invocationContext().invocationId();
        if (event instanceof AiServiceStartedEvent) {
            AiServiceInvocation started = startAiServiceInvocation(agentAiServiceEvent);
            if (started != null) {
                aiServiceInvocations.put(invocationId, started);
            }
            return;
        }
        AiServiceInvocation invocation = aiServiceInvocations.get(invocationId);
        if (invocation == null) {
            return;
        }
        String agentName = agentAiServiceEvent.agentName();
        if (event instanceof AiServiceRequestIssuedEvent requestIssued) {
            invocation.inferenceCalls.incrementAndGet();
            invocation.offeredTools = requestIssued.request().toolSpecifications();
        } else if (event instanceof AiServiceResponseReceivedEvent responseReceived) {
            invocation.addTokens(responseReceived.response().tokenUsage());
        } else if (event instanceof ToolExecutedEvent) {
            invocation.toolCalls.incrementAndGet();
        } else if (event instanceof AiServiceCompletedEvent) {
            recordAiServiceInvocation(invocationId, agentName, OUTCOME_SUCCESS, NO_ERROR);
        } else if (event instanceof AiServiceErrorEvent error) {
            recordAiServiceInvocation(invocationId, agentName, OUTCOME_ERROR, errorType(error.error()));
        } else if (event instanceof AiServiceCancelledEvent) {
            recordAiServiceInvocation(invocationId, agentName, OUTCOME_CANCELLED, NO_ERROR);
        }
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    /**
     * Starts timing an agent by its AI Service, taking over the tags and the active invocation of its invocation, which
     * is then no longer recorded by its invocation callbacks; an agent whose AI Service never starts, like a remote A2A
     * agent or an LLM-based agent failing before its AI Service starts, is recorded from its invocation callbacks
     * instead. The AI Service of an agent starts on the thread of its invocation, before any other agent is invoked
     * there, also for the items of a parallel mapper, whose AI Service events carry the agent they map rather than the
     * item. An AI Service started without such an invocation, like the one of an agent called by a tool or of a
     * subscription to a reactive stream, is timed on its own; one whose invocation is not recorded, as its tags could
     * not be computed, is not timed.
     */
    private AiServiceInvocation startAiServiceInvocation(AgentAiServiceEvent agentAiServiceEvent) {
        AgentInstance agent = agentAiServiceEvent.agent();
        PendingInvocation pending = startingInvocation.get();
        if (pending == null) {
            Tags customTags = customTags(agentAiServiceEvent.agenticScope());
            return new AiServiceInvocation(agent, customTags, startActive(agent, customTags), System.nanoTime());
        }
        startingInvocation.remove();
        pendingInvocations.remove(pending.key, pending);
        if (pending.skipped()) {
            return null;
        }
        return new AiServiceInvocation(agent, pending.customTags, pending.takeActive(), System.nanoTime());
    }

    /**
     * Returns whether an agent returns a reactive stream: its AI Service starts once per subscription, each timed on
     * its own, and not when the agent returns the stream.
     */
    private static boolean returnsReactiveStream(AgentInstance agent) {
        Type outputType = agent.outputType();
        if (outputType == null) {
            return false;
        }
        Type rawType = outputType instanceof ParameterizedType parameterized ? parameterized.getRawType() : outputType;
        if (rawType instanceof Class<?> rawClass && Flow.Publisher.class.isAssignableFrom(rawClass)) {
            return true;
        }
        for (PublisherAdapter adapter : PUBLISHER_ADAPTERS) {
            if (adapter.canAdapt(outputType)) {
                return true;
            }
        }
        return false;
    }

    private Tags customTags(AgenticScope agenticScope) {
        return tags == null ? Tags.empty() : Tags.of(ensureNotNull(tags.apply(agenticScope), "tags"));
    }

    private LongTaskTimer.Sample startActive(AgentInstance agent, Tags customTags) {
        return activeInvocations
                .withTags(customTags, List.of(operation(agent).value(), definitionName(agent)))
                .start();
    }

    private static List<String> toolTagValues(String toolName, String agentName, String outcome, String errorType) {
        return List.of(toolName, FUNCTION_TOOL, agentName, outcome, errorType);
    }

    private static OTelGenAiOperationName operation(AgentInstance agent) {
        return agent.leaf() ? OTelGenAiOperationName.INVOKE_AGENT : OTelGenAiOperationName.INVOKE_WORKFLOW;
    }

    private static InvocationKey invocationKey(AgenticScope agenticScope, String agentId) {
        return new InvocationKey(agenticScope.memoryId(), agentId, Thread.currentThread());
    }

    private void recordAiServiceInvocation(UUID invocationId, String agentName, String outcome, String errorType) {
        AiServiceInvocation invocation = aiServiceInvocations.remove(invocationId);
        if (invocation == null) {
            return;
        }
        invocation.endActive();
        long endNanos = System.nanoTime();
        Tags customTags = invocation.customTags;
        invocationDuration
                .withTags(
                        customTags, List.of(OTelGenAiOperationName.INVOKE_AGENT.value(), agentName, outcome, errorType))
                .record(endNanos - invocation.startNanos, TimeUnit.NANOSECONDS);
        List<String> agentTagValues = List.of(OTelGenAiOperationName.INVOKE_AGENT.value(), agentName);
        inferenceCalls.withTags(customTags, agentTagValues).record(invocation.inferenceCalls.get());
        toolCalls.withTags(customTags, agentTagValues).record(invocation.toolCalls.get());
        invocation.recordTokens(OTelGenAiOperationName.INVOKE_AGENT, agentName);
        invocation.recordOpenTools(agentName, OUTCOME_ERROR.equals(outcome) ? errorType : OTHER_ERROR, endNanos);
        countInvocation(invocation.agent, customTags, agentName, outcome);
    }

    private void recordInvocation(PendingInvocation pending, String outcome, String errorType) {
        pending.endActive();
        String name = definitionName(pending.agent);
        OTelGenAiOperationName operation = operation(pending.agent);
        invocationDuration
                .withTags(pending.customTags, List.of(operation.value(), name, outcome, errorType))
                .record(System.nanoTime() - pending.startNanos, TimeUnit.NANOSECONDS);
        if (operation == OTelGenAiOperationName.INVOKE_WORKFLOW) {
            pending.recordTokens(operation, name);
        }
        countInvocation(pending.agent, pending.customTags, name, outcome);
    }

    private void countInvocation(AgentInstance agent, Tags customTags, String agentName, String outcome) {
        AgentInstance parent = agent.parent();
        if (parent == null) {
            return;
        }
        invocations
                .withTags(customTags, List.of(agentName, definitionName(parent), outcome))
                .increment();
    }

    private DistributionSummary tokenSummary(
            Tags customTags, OTelGenAiOperationName operation, String name, OTelGenAiTokenType tokenType) {
        return tokenUsage.withTags(customTags, List.of(operation.value(), name, tokenType.value()));
    }

    int pendingInvocations() {
        return pendingInvocations.size() + aiServiceInvocations.size();
    }

    /**
     * Returns the name an agent is defined with: an instance created for one item of a parallel mapper has the agent
     * id of the mapped subagent followed by {@code _} and the index of the item, and is reported under the name of
     * that subagent.
     */
    private static String definitionName(AgentInstance agent) {
        AgentInstance parent = agent.parent();
        if (parent != null) {
            String agentId = agent.agentId();
            for (AgentInstance subagent : parent.subagents()) {
                String subagentId = subagent.agentId();
                if (agentId.length() > subagentId.length()
                        && agentId.startsWith(subagentId)
                        && agentId.charAt(subagentId.length()) == '_') {
                    return subagent.name();
                }
            }
        }
        return agent.name();
    }

    /**
     * Returns the class name of the exception that caused a failure, not of the exceptions wrapping it on its way
     * through the agents containing the failing one and their proxies, so that a workflow and its failing subagent
     * report the same type.
     */
    private static String errorType(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof AgentInvocationException
                        || cause instanceof InvocationTargetException
                        || cause instanceof UndeclaredThrowableException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getName();
    }

    /**
     * Identifies the invocation of an agent between its invocation callbacks: the before and the after callbacks of
     * one invocation always run on the same thread, so overlapping invocations of the same agent in one scope, on
     * different threads, are kept apart.
     */
    private record InvocationKey(Object memoryId, String agentId, Thread thread) {}

    /**
     * One invocation being measured: its agent, the tags provided for it, its active invocation, and the tokens of its
     * model calls, summed per token type; a type no call reported is not recorded, as 0 would be wrong.
     */
    private abstract class Invocation {

        final AgentInstance agent;
        final Tags customTags;
        final long startNanos;
        private LongTaskTimer.Sample active;
        private long inputTokens = -1;
        private long outputTokens = -1;

        Invocation(AgentInstance agent, Tags customTags, LongTaskTimer.Sample active, long startNanos) {
            this.agent = agent;
            this.customTags = customTags;
            this.active = active;
            this.startNanos = startNanos;
        }

        synchronized LongTaskTimer.Sample takeActive() {
            LongTaskTimer.Sample taken = active;
            active = null;
            return taken;
        }

        synchronized void endActive() {
            if (active != null) {
                active.stop();
                active = null;
            }
        }

        synchronized void addTokens(TokenUsage usage) {
            if (usage == null) {
                return;
            }
            if (usage.inputTokenCount() != null) {
                inputTokens = Math.max(inputTokens, 0) + usage.inputTokenCount();
            }
            if (usage.outputTokenCount() != null) {
                outputTokens = Math.max(outputTokens, 0) + usage.outputTokenCount();
            }
        }

        synchronized void recordTokens(OTelGenAiOperationName operation, String name) {
            if (inputTokens >= 0) {
                tokenSummary(customTags, operation, name, OTelGenAiTokenType.INPUT)
                        .record(inputTokens);
            }
            if (outputTokens >= 0) {
                tokenSummary(customTags, operation, name, OTelGenAiTokenType.OUTPUT)
                        .record(outputTokens);
            }
        }
    }

    private final class PendingInvocation extends Invocation {

        private final InvocationKey key;
        private final boolean skipped;

        private PendingInvocation(
                InvocationKey key, AgentInstance agent, Tags customTags, LongTaskTimer.Sample active, long startNanos) {
            super(agent, customTags, active, startNanos);
            this.key = key;
            this.skipped = false;
        }

        /**
         * Creates an invocation that is not recorded, as its tags could not be computed.
         */
        private PendingInvocation(InvocationKey key, AgentInstance agent) {
            super(agent, Tags.empty(), null, 0);
            this.key = key;
            this.skipped = true;
        }

        private boolean skipped() {
            return skipped;
        }
    }

    private final class AiServiceInvocation extends Invocation {

        private final AtomicInteger inferenceCalls = new AtomicInteger();
        private final AtomicInteger toolCalls = new AtomicInteger();
        private volatile List<ToolSpecification> offeredTools = List.of();
        private ToolExecutionRequest openTool;
        private long openToolStartNanos;
        private Map<ToolExecutionRequest, Long> otherOpenTools;

        private AiServiceInvocation(
                AgentInstance agent, Tags customTags, LongTaskTimer.Sample active, long startNanos) {
            super(agent, customTags, active, startNanos);
        }

        /**
         * Tools executed sequentially, the default, are open one at a time, so the start of a single open tool is kept
         * without allocating; tools executed concurrently go to a map.
         */
        private synchronized void toolStarted(ToolExecutionRequest request, long nanos) {
            if (openTool == null) {
                openTool = request;
                openToolStartNanos = nanos;
            } else {
                if (otherOpenTools == null) {
                    otherOpenTools = new HashMap<>();
                }
                otherOpenTools.put(request, nanos);
            }
        }

        /**
         * Returns the name of a requested tool, or {@code _OTHER} for a tool that the model asked for but was not
         * offered, as the model can invent any name and each would create new series.
         */
        private String toolName(ToolExecutionRequest request) {
            String name = request.name();
            for (ToolSpecification tool : offeredTools) {
                if (tool.name().equals(name)) {
                    return name;
                }
            }
            return OTHER_TOOL;
        }

        private synchronized void toolEnded(ToolExecutionRequest request) {
            if (request.equals(openTool)) {
                openTool = null;
            } else if (otherOpenTools != null) {
                otherOpenTools.remove(request);
            }
        }

        /**
         * Records the tool executions that started but never ended: their failure propagated and ended the invocation
         * of the AI Service, so the tool execution listener was not notified of their end, which is then taken as the
         * end of the invocation. When several were running, the exception of each is not known.
         */
        private synchronized void recordOpenTools(String agentName, String failureType, long endNanos) {
            int open = (openTool == null ? 0 : 1) + (otherOpenTools == null ? 0 : otherOpenTools.size());
            String errorType = open > 1 ? OTHER_ERROR : failureType;
            if (openTool != null) {
                recordOpenTool(openTool, openToolStartNanos, agentName, errorType, endNanos);
                openTool = null;
            }
            if (otherOpenTools != null) {
                otherOpenTools.forEach(
                        (request, start) -> recordOpenTool(request, start, agentName, errorType, endNanos));
                otherOpenTools = null;
            }
        }

        private void recordOpenTool(
                ToolExecutionRequest request, long startNanos, String agentName, String errorType, long endNanos) {
            toolDuration
                    .withTags(customTags, toolTagValues(toolName(request), agentName, OUTCOME_ERROR, errorType))
                    .record(endNanos - startNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * @return a builder of {@link MicrometerMetricsAgentListener}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link MicrometerMetricsAgentListener}.
     */
    public static final class Builder {

        private MeterRegistry meterRegistry;
        private Function<AgenticScope, Iterable<Tag>> tags;

        Builder() {}

        /**
         * Required: {@link #build()} fails if it is not set.
         *
         * @param meterRegistry the {@link MeterRegistry} to register metrics with
         * @return this builder
         */
        public Builder meterRegistry(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return this;
        }

        /**
         * Adds tags of your own to every meter recorded for an invocation, for instance the tenant read from the state
         * of its {@link AgenticScope}. The function is called once, when the invocation starts, and must return the
         * same tag keys for every invocation, as some registries (e.g., Prometheus) require all the meters of a name
         * to have the same tags, and values from a bounded set, as each distinct value creates new series: never a
         * user, a memory id or a request id. The tags of the listener win over added tags with the same key. When the
         * function throws, the invocation is not recorded; the agent is not affected.
         *
         * @param tags the function giving the tags to add from the {@link AgenticScope} of an invocation
         * @return this builder
         */
        public Builder tags(Function<AgenticScope, Iterable<Tag>> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * @return a new {@link MicrometerMetricsAgentListener}
         */
        public MicrometerMetricsAgentListener build() {
            return new MicrometerMetricsAgentListener(this);
        }
    }
}
