package dev.langchain4j.agentic.supervisor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import dev.langchain4j.agentic.internal.Context;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.ChatMemoryAccessProvider;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toMap;

public class SupervisorPlanner implements Planner, ChatMemoryAccessProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SupervisorPlanner.class);
    public static final String SUPERVISOR_CONTEXT_KEY = "supervisorContext";
    public static final String SUPERVISOR_CONTEXT_PREFIX = "Use the following supervisor context to better understand "
            + "constraints, policies or preferences when creating the plan ";

    private final ChatModel chatModel;

    private final ChatMemoryProvider chatMemoryProvider;

    private final int maxAgentsInvocations;
    private int loopCount = 1;

    private ResponseAgent responseAgent;

    private final SupervisorContextStrategy contextStrategy;
    private final SupervisorResponseStrategy responseStrategy;

    private final Function<AgenticScope, String> requestGenerator;

    private final String outputKey;

    private final Function<AgenticScope, Object> output;

    private Map<String, AgentInstance> agents;
    private String agentsList;

    private String request;

    public SupervisorPlanner(ChatModel chatModel, ChatMemoryProvider chatMemoryProvider, int maxAgentsInvocations,
                             SupervisorContextStrategy contextStrategy, SupervisorResponseStrategy responseStrategy,
                             Function<AgenticScope, String> requestGenerator, String outputKey, Function<AgenticScope, Object> output) {
        this.chatModel = chatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.maxAgentsInvocations = maxAgentsInvocations;
        this.contextStrategy = contextStrategy;
        this.responseStrategy = responseStrategy;
        this.requestGenerator = requestGenerator;
        this.outputKey = outputKey;
        this.output = output;
    }

    @Override
    public void init(AgenticScope agenticScope, List<AgentInstance> agents) {
        this.agents = agents.stream().collect(toMap(AgentInstance::uniqueName, Function.identity()));
        this.agentsList = agents.stream()
                .map(AgentInstance::toCard)
                .collect(Collectors.joining(", "));

        this.request = requestGenerator != null ? requestGenerator.apply(agenticScope) : agenticScope.readState("request", "");
        if (responseStrategy == SupervisorResponseStrategy.SCORED) {
            this.responseAgent = AiServices.builder(ResponseAgent.class).chatModel(chatModel).build();
        }
    }

    @Override
    public Action firstAction(AgenticScope agenticScope) {
        return nextSubagent(agenticScope, "");
    }

    @Override
    public Action nextAction(AgenticScope agenticScope, AgentExecution lastAgentExecution) {
        if (loopCount++ >= maxAgentsInvocations) {
            return Action.done();
        }
        return nextSubagent(agenticScope, lastAgentExecution.output().toString());
    }

    private Action nextSubagent(AgenticScope agenticScope, String lastResponse) {
        String supervisorContext = agenticScope.hasState(SUPERVISOR_CONTEXT_KEY)
                ? SUPERVISOR_CONTEXT_PREFIX + "'" + agenticScope.readState(SUPERVISOR_CONTEXT_KEY, "") + "'."
                : "";

        AgentInvocation agentInvocation = planner(agenticScope).plan(agenticScope.memoryId(), agentsList, request, lastResponse, supervisorContext);
        LOG.info("Agent Invocation: {}", agentInvocation);

        if (agentInvocation.getAgentName().equalsIgnoreCase("done")) {
            if (outputKey != null) {
                Object result = result(agenticScope, request, lastResponse, agentInvocation);
                agenticScope.writeState(outputKey, result);
            }
            return Action.done();
        }

        String agentName = agentInvocation.getAgentName();
        AgentInstance agent = agents.get(agentName);
        if (agent == null) {
            throw new IllegalStateException("No agent found with name: " + agentName);
        }

        agentInvocation.getArguments().forEach(agenticScope::writeState);
        return Action.call(agent);
    }

    private PlannerAgent planner(AgenticScope agenticScope) {
        return ((DefaultAgenticScope) agenticScope).getOrCreateAgent(agentId(), this::buildPlannerAgent);
    }

    private Object result(AgenticScope agenticScope, String request, String lastResponse, AgentInvocation done) {
        if (output != null) {
            return output.apply(agenticScope);
        }
        String doneResponse = done != null ? done.getArguments().get("response") : null;
        if (doneResponse == null) {
            return lastResponse;
        }
        return switch (responseStrategy) {
            case LAST -> lastResponse;
            case SUMMARY -> doneResponse;
            case SCORED -> {
                ResponseScore score = responseAgent.scoreResponses(request, lastResponse, doneResponse);
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
                builder.chatRequestTransformer(new Context.Summarizer(agenticScope, chatModel));
            }
        } else {
            switch (contextStrategy) {
                case CHAT_MEMORY:
                    builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20));
                    break;
                case SUMMARIZATION:
                    builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(2))
                            .chatRequestTransformer(new Context.Summarizer(agenticScope, chatModel));
                    break;
                case CHAT_MEMORY_AND_SUMMARIZATION:
                    builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                            .chatRequestTransformer(new Context.Summarizer(agenticScope, chatModel));
                    break;
            }
        }
    }

    private String agentId() {
        return outputKey + "@Supervisor";
    }

    @Override
    public ChatMemoryAccess chatMemoryAccess(AgenticScope agenticScope) {
        return planner(agenticScope);
    }
}
