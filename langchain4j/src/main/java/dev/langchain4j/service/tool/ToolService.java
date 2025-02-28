package dev.langchain4j.service.tool;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.IllegalConfigurationException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ToolService {

    private static final int MAX_SEQUENTIAL_TOOL_EXECUTIONS = 100;

    private List<ToolSpecification> toolSpecifications;
    private Map<String, ToolExecutor> toolExecutors;
    private ToolProvider toolProvider;

    private Function<ToolExecutionRequest, ToolExecutionResultMessage> toolHallucinationStrategy =
            HallucinatedToolNameStrategy.THROW_EXCEPTION;

    public void hallucinatedToolNameStrategy(
            Function<ToolExecutionRequest, ToolExecutionResultMessage> toolHallucinationStrategy) {
        this.toolHallucinationStrategy = toolHallucinationStrategy;
    }

    public void toolProvider(ToolProvider toolProvider) {
        if (toolSpecifications != null || toolExecutors != null) {
            throw new IllegalArgumentException(
                    "Either the tools or the tool provider can be configured, but not both!");
        }
        this.toolProvider = toolProvider;
    }

    public void tools(Map<ToolSpecification, ToolExecutor> tools) {
        if (toolProvider != null) {
            throw new IllegalArgumentException(
                    "Either the tools or the tool provider can be configured, but not both!");
        }
        initTools();

        tools.forEach((toolSpecification, toolExecutor) -> {
            toolSpecifications.add(toolSpecification);
            toolExecutors.put(toolSpecification.name(), toolExecutor);
        });
    }

    public void tools(Collection<Object> objectsWithTools) {
        if (toolProvider != null) {
            throw new IllegalArgumentException(
                    "Either the tools or the tool provider can be configured, but not both!");
        }
        initTools();

        for (Object objectWithTool : objectsWithTools) {
            if (objectWithTool instanceof Class) {
                throw illegalConfiguration("Tool '%s' must be an object, not a class", objectWithTool);
            }

            for (Method method : objectWithTool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    ToolSpecification toolSpecification = toolSpecificationFrom(method);
                    if (toolExecutors.containsKey(toolSpecification.name())) {
                        throw new IllegalConfigurationException(
                                "Duplicated definition for tool: " + toolSpecification.name());
                    }
                    toolExecutors.put(toolSpecification.name(), new DefaultToolExecutor(objectWithTool, method));
                    toolSpecifications.add(toolSpecificationFrom(method));
                }
            }
        }
    }

    public void initTools() {
        if (toolSpecifications == null) {
            toolSpecifications = new ArrayList<>();
        }
        if (toolExecutors == null) {
            toolExecutors = new HashMap<>();
        }
    }

    public ToolExecutionContext executionContext(Object memoryId, UserMessage userMessage) {
        if (this.toolProvider == null) {
            return new ToolExecutionContext(this.toolSpecifications, this.toolExecutors);
        }

        List<ToolSpecification> toolsSpecs = new ArrayList<>();
        Map<String, ToolExecutor> toolExecs = new HashMap<>();
        ToolProviderRequest toolProviderRequest = new ToolProviderRequest(memoryId, userMessage);
        ToolProviderResult toolProviderResult = toolProvider.provideTools(toolProviderRequest);
        if (toolProviderResult != null) {
            for (Map.Entry<ToolSpecification, ToolExecutor> entry :
                    toolProviderResult.tools().entrySet()) {
                toolsSpecs.add(entry.getKey());
                toolExecs.put(entry.getKey().name(), entry.getValue());
            }
        }
        return new ToolExecutionContext(toolsSpecs, toolExecs);
    }

    public ToolExecutionResult executeInferenceAndToolsLoop(
            ChatResponse chatResponse,
            ChatRequestParameters parameters,
            List<ChatMessage> messages,
            ChatLanguageModel chatModel,
            ChatMemory chatMemory,
            Object memoryId,
            Map<String, ToolExecutor> toolExecutors) {
        TokenUsage tokenUsageAccumulator = chatResponse.metadata().tokenUsage();
        int executionsLeft = MAX_SEQUENTIAL_TOOL_EXECUTIONS;
        List<ToolExecution> toolExecutions = new ArrayList<>();

        while (true) {

            if (executionsLeft-- == 0) {
                throw runtime(
                        "Something is wrong, exceeded %s sequential tool executions", MAX_SEQUENTIAL_TOOL_EXECUTIONS);
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

            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());

                ToolExecutionResultMessage toolExecutionResultMessage = toolExecutor == null
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

        return new ToolExecutionResult(chatResponse, toolExecutions, tokenUsageAccumulator);
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
}
