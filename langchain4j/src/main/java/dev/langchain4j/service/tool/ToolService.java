package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.internal.Utils.getAnnotatedMethod;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ReturnBehavior;
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
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.IllegalConfigurationException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Internal
public class ToolService {

    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    private final Map<String, ToolExecutor> toolExecutors = new HashMap<>();
    private final Set<String> immediateReturnTools = new HashSet<>();
    private ToolProvider toolProvider;
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
        toolSpecifications.add(toolSpecification);

        if (method.getAnnotation(Tool.class).returnBehavior() == ReturnBehavior.IMMEDIATE) {
            immediateReturnTools.add(toolSpecification.name());
        }
    }

    public void maxSequentialToolsInvocations(int maxSequentialToolsInvocations) {
        this.maxSequentialToolsInvocations = maxSequentialToolsInvocations;
    }

    public ToolServiceContext createContext(Object memoryId, UserMessage userMessage) {
        if (this.toolProvider == null) {
            return this.toolSpecifications.isEmpty() ?
                    ToolServiceContext.Empty.INSTANCE :
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
            Map<String, ToolExecutor> toolExecutors,
            boolean isResultType) {
        TokenUsage tokenUsageAccumulator = chatResponse.metadata().tokenUsage();
        int executionsLeft = maxSequentialToolsInvocations;
        List<ToolExecution> toolExecutions = new ArrayList<>();

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

            boolean immediateToolReturn = isResultType;
            ToolExecutionResultMessage toolExecutionResultMessage = null;
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());

                toolExecutionResultMessage = toolExecutor == null
                        ? applyToolHallucinationStrategy(toolExecutionRequest)
                        : ToolExecutionResultMessage.from(
                                toolExecutionRequest, toolExecutor.execute(toolExecutionRequest, memoryId));

                toolExecutions.add(ToolExecution.builder()
                        .request(toolExecutionRequest)
                        .result(toolExecutionResultMessage.text())
                        .build());

                if (chatMemory != null) {
                    chatMemory.add(toolExecutionResultMessage);
                } else {
                    messages.add(toolExecutionResultMessage);
                }

                immediateToolReturn = immediateToolReturn && isImmediateTool(toolExecutionRequest.name());
            }

            if (immediateToolReturn && toolExecutionResultMessage != null) {
                return new ToolServiceResult(toolExecutions);
            }

            if (chatMemory != null) {
                messages = chatMemory.messages();
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .parameters(parameters)
                    .build();

            chatResponse = chatModel.chat(chatRequest);

            tokenUsageAccumulator = TokenUsage.sum(
                    tokenUsageAccumulator, chatResponse.metadata().tokenUsage());
        }

        chatResponse = ChatResponse.builder()
                .aiMessage(chatResponse.aiMessage())
                .metadata(chatResponse.metadata().toBuilder()
                        .tokenUsage(tokenUsageAccumulator)
                        .build())
                .build();

        return new ToolServiceResult(chatResponse, toolExecutions);
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

    public ToolProvider toolProvider() {
        return toolProvider;
    }

    public boolean isImmediateTool(String toolName) {
        return immediateReturnTools.contains(toolName);
    }
}
