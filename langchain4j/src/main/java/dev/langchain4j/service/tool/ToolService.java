package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static java.util.concurrent.TimeUnit.SECONDS;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.IllegalConfigurationException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

@Internal
public class ToolService {

    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    private final Map<String, ToolExecutor> toolExecutors = new HashMap<>();
    private ToolProvider toolProvider;
    private Executor executor;
    private int maxSequentialToolsInvocations = 100;

    private Function<ToolExecutionRequest, ToolExecutionResultMessage> toolHallucinationStrategy =
            HallucinatedToolNameStrategy.THROW_EXCEPTION;

    public void hallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> toolHallucinationStrategy) {
        this.toolHallucinationStrategy = toolHallucinationStrategy;
    }

    public void toolProvider(ToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    public void tools(Map<ToolSpecification, ToolExecutor> tools) {
        tools.forEach((toolSpecification, toolExecutor) -> {
            toolSpecifications.add(toolSpecification);
            toolExecutors.put(toolSpecification.name(), toolExecutor);
        });
    }

    public void tools(Collection<Object> objectsWithTools) {
        for (Object objectWithTool : objectsWithTools) {
            if (objectWithTool instanceof Class) {
                throw illegalConfiguration("Tool '%s' must be an object, not a class", objectWithTool);
            }

            for (Method method : objectWithTool.getClass().getDeclaredMethods()) {
                getAnnotatedMethod(method, Tool.class).ifPresent( toolMethod -> processToolMethod(objectWithTool, toolMethod) );
            }
        }
    }

    private void processToolMethod(Object objectWithTool, Method method) {
        ToolSpecification toolSpecification = toolSpecificationFrom(method);
        if (toolExecutors.containsKey(toolSpecification.name())) {
            throw new IllegalConfigurationException(
                    "Duplicated definition for tool: " + toolSpecification.name());
        }
        toolExecutors.put(toolSpecification.name(), new DefaultToolExecutor(objectWithTool, method));
        toolSpecifications.add(toolSpecificationFrom(method));
    }

    /**
     * @since 1.4.0
     */
    public void executeToolsConcurrently() {
        this.executor = defaultExecutor();
    }

    /**
     * @since 1.4.0
     */
    public void executeToolsConcurrently(Executor executor) {
        this.executor = getOrDefault(executor, ToolService::defaultExecutor);
    }

    private static Executor defaultExecutor() {
        return DefaultExecutorHolder.INSTANCE;
    }

    private static class DefaultExecutorHolder {
        private static final Executor INSTANCE = createDefaultExecutor();
    }

    private static Executor createDefaultExecutor() {
        return new ThreadPoolExecutor(
                0, Integer.MAX_VALUE,
                1, SECONDS,
                new SynchronousQueue<>()
        );
    }

    public void maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        this.maxSequentialToolsInvocations = maxSequentialToolsInvocations;
    }

    public ToolServiceContext createContext(Object memoryId, UserMessage userMessage) {
        if (this.toolProvider == null) {
            return this.toolSpecifications.isEmpty() ?
                    new ToolServiceContext(null, null) :
                    new ToolServiceContext(this.toolSpecifications, this.toolExecutors);
        }

        List<ToolSpecification> toolsSpecs = new ArrayList<>(this.toolSpecifications);
        Map<String, ToolExecutor> toolExecs = new HashMap<>(this.toolExecutors);
        ToolProviderRequest toolProviderRequest = new ToolProviderRequest(memoryId, userMessage);
        ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest);
        if (toolProviderResult != null) {
            for (Map.Entry<ToolSpecification, ToolExecutor> entry :
                    toolProviderResult.tools().entrySet()) {
                if (toolExecs.putIfAbsent(entry.getKey().name(), entry.getValue()) == null) {
                    toolsSpecs.add(entry.getKey());
                } else {
                    throw new IllegalConfigurationException(
                            "Duplicated definition for tool: " + entry.getKey().name());
                }
            }
        }
        return new ToolServiceContext(toolsSpecs, toolExecs);
    }

    public ToolServiceResult executeInferenceAndToolsLoop(
            ChatResponse chatResponse,
            ChatRequestParameters parameters,
            List<ChatMessage> messages,
            ChatModel chatModel,
            ChatMemory chatMemory,
            Object memoryId,
            Map<String, ToolExecutor> toolExecutors) {

        TokenUsage aggregateTokenUsage = chatResponse.metadata().tokenUsage();
        List<ToolExecution> toolExecutions = new ArrayList<>();
        List<ChatResponse> intermediateResponses = new ArrayList<>();

        int executionsLeft = maxSequentialToolsInvocations;
        while (true) {

            if (executionsLeft-- == 0) {
                throw runtime(
                        "Something is wrong, exceeded %s sequential tool executions", maxSequentialToolsInvocations);
            }

            AiMessage aiMessage = chatResponse.aiMessage();

            if (chatMemory != null) {
                chatMemory.add(aiMessage);
            } else {
                messages = new ArrayList<>(messages);
                messages.add(aiMessage);
            }

            if (!aiMessage.hasToolExecutionRequests()) {
                break;
            }

            intermediateResponses.add(chatResponse);

            Map<ToolExecutionRequest, ToolExecutionResultMessage> toolResults =
                    execute(aiMessage.toolExecutionRequests(), toolExecutors, memoryId);

            for (Map.Entry<ToolExecutionRequest, ToolExecutionResultMessage> entry : toolResults.entrySet()) {
                ToolExecutionRequest toolExecutionRequest = entry.getKey();
                ToolExecutionResultMessage toolExecutionResultMessage = entry.getValue();

                ToolExecution toolExecution = ToolExecution.builder()
                        .request(toolExecutionRequest)
                        .result(toolExecutionResultMessage.text())
                        .build();
                toolExecutions.add(toolExecution);

                if (chatMemory != null) {
                    chatMemory.add(toolExecutionResultMessage);
                } else {
                    messages.add(toolExecutionResultMessage);
                }
            }

            if (chatMemory != null) {
                messages = chatMemory.messages();
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .parameters(parameters)
                    .build();

            chatResponse = chatModel.chat(chatRequest);
            aggregateTokenUsage = TokenUsage.sum(aggregateTokenUsage, chatResponse.metadata().tokenUsage());
        }

        return ToolServiceResult.builder()
                .intermediateResponses(intermediateResponses)
                .finalResponse(chatResponse)
                .toolExecutions(toolExecutions)
                .aggregateTokenUsage(aggregateTokenUsage)
                .build();
    }

    private Map<ToolExecutionRequest, ToolExecutionResultMessage> execute(
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<String, ToolExecutor> toolExecutors,
            Object memoryId) {
        if (executor != null && toolExecutionRequests.size() > 1) {
            return executeConcurrently(toolExecutionRequests, toolExecutors, memoryId);
        } else {
            // when there is only one tool to execute, it doesn't make sense to do it in a separate thread
            return executeSequentially(toolExecutionRequests, toolExecutors, memoryId);
        }
    }

    private Map<ToolExecutionRequest, ToolExecutionResultMessage> executeConcurrently(
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<String, ToolExecutor> toolExecutors,
            Object memoryId) {
        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResultMessage>> futures = new LinkedHashMap<>();

        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            CompletableFuture<ToolExecutionResultMessage> future = CompletableFuture.supplyAsync(() -> {
                ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());
                if (toolExecutor == null) {
                    return applyToolHallucinationStrategy(toolExecutionRequest);
                }
                String toolResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                return ToolExecutionResultMessage.from(toolExecutionRequest, toolResult);
            }, executor);
            futures.put(toolExecutionRequest, future);
        }

        Map<ToolExecutionRequest, ToolExecutionResultMessage> results = new LinkedHashMap<>();
        for (Map.Entry<ToolExecutionRequest, CompletableFuture<ToolExecutionResultMessage>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        return results;
    }

    private Map<ToolExecutionRequest, ToolExecutionResultMessage> executeSequentially(
            List<ToolExecutionRequest> toolExecutionRequests,
            Map<String, ToolExecutor> toolExecutors,
            Object memoryId) {
        Map<ToolExecutionRequest, ToolExecutionResultMessage> toolResults = new LinkedHashMap<>();
        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());
            ToolExecutionResultMessage toolExecutionResultMessage;
            if (toolExecutor == null) {
                toolExecutionResultMessage = applyToolHallucinationStrategy(toolExecutionRequest);
            } else {
                String toolResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest, toolResult);
            }
            toolResults.put(toolExecutionRequest, toolExecutionResultMessage);
        }
        return toolResults;
    }

    public ToolExecutionResultMessage applyToolHallucinationStrategy(ToolExecutionRequest toolExecutionRequest) {
        return toolHallucinationStrategy.apply(toolExecutionRequest);
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    public Map<String, ToolExecutor> toolExecutors() {
        return toolExecutors;
    }

    /**
     * @since 1.4.0
     */
    public Executor executor() {
        return executor;
    }

    public ToolProvider toolProvider() {
        return toolProvider;
    }
}
