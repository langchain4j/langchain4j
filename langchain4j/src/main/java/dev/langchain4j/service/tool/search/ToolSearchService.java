package dev.langchain4j.service.tool.search;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolServiceContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.merge;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

/**
 * @since 1.12.0
 */
@Internal
public class ToolSearchService {

    private static final String FOUND_TOOLS_ATTRIBUTE = "found_tools"; // do not change, will break backward compatibility!

    private final ToolSearchStrategy strategy;

    public ToolSearchService(ToolSearchStrategy toolSearchStrategy) {
        this.strategy = ensureNotNull(toolSearchStrategy, "toolSearchStrategy");
    }

    public ToolServiceContext adjust(ToolServiceContext toolServiceContext,
                                     ChatMemory chatMemory,
                                     InvocationContext invocationContext) {
        List<ToolSpecification> toolSearchTools = strategy.getToolSearchTools(invocationContext);
        List<ToolSpecification> availableTools = toolServiceContext.availableTools();
        List<ToolSpecification> effectiveTools = calculateEffectiveTools(toolSearchTools, availableTools, chatMemory);
        List<ToolSpecification> searchableTools = calculateSearchableTools(availableTools, effectiveTools);
        Map<String, ToolExecutor> toolSearchToolExecutors = createExecutors(toolSearchTools, searchableTools);
        return toolServiceContext.toBuilder()
                .effectiveTools(effectiveTools)
                .toolExecutors(merge(toolServiceContext.toolExecutors(), toolSearchToolExecutors))
                .build();
    }

    private List<ToolSpecification> calculateEffectiveTools(List<ToolSpecification> toolSearchTools,
                                                            List<ToolSpecification> availableTools,
                                                            ChatMemory chatMemory) {
        if (chatMemory == null) {
            return toolSearchTools;
        }

        Set<String> toolNamesFoundEarlier = chatMemory.messages().stream()
                .filter(it -> it instanceof ToolExecutionResultMessage)
                .map(it -> (ToolExecutionResultMessage) it)
                .map(it -> it.attributes().get(FOUND_TOOLS_ATTRIBUTE))
                .filter(it -> it != null)
                .map(it -> (List<String>) it)
                .flatMap(List::stream)
                .collect(toCollection(() -> new LinkedHashSet<>()));

        if (toolNamesFoundEarlier.isEmpty()) {
            return toolSearchTools;
        }

        Map<String, ToolSpecification> toolsByName = new HashMap<>(availableTools.size());
        availableTools.forEach(tool -> toolsByName.put(tool.name(), tool));

        List<ToolSpecification> effectiveTools = new ArrayList<>(toolSearchTools);
        toolNamesFoundEarlier.forEach(toolName -> effectiveTools.add(toolsByName.get(toolName)));
        return effectiveTools;
    }

    private List<ToolSpecification> calculateSearchableTools(List<ToolSpecification> availableTools,
                                                             List<ToolSpecification> effectiveTools) {
        Set<ToolSpecification> searchableTools = new LinkedHashSet<>(availableTools);
        searchableTools.removeAll(effectiveTools);
        return new ArrayList<>(searchableTools);
    }

    private Map<String, ToolExecutor> createExecutors(List<ToolSpecification> toolSearchTools,
                                                      List<ToolSpecification> searchableTools) {
        Map<String, ToolExecutor> executors = new HashMap<>();
        for (ToolSpecification toolSearchTool : toolSearchTools) {
            executors.put(toolSearchTool.name(), new ToolSearchExecutor(searchableTools));
        }
        return executors;
    }

    public static ChatRequestParameters addFoundTools(ChatRequestParameters parameters,
                                                      Collection<ToolExecutionResult> toolResults,
                                                      List<ToolSpecification> availableTools) {
        Set<String> foundToolNames = new LinkedHashSet<>();
        for (ToolExecutionResult toolResult : toolResults) {
            Object attribute = toolResult.attributes().get(FOUND_TOOLS_ATTRIBUTE);
            if (attribute != null && attribute instanceof List<?> foundToolNamesList) {
                foundToolNames.addAll((List<String>) foundToolNamesList);
            }
        }
        if (foundToolNames.isEmpty()) {
            return parameters;
        }

        Set<String> effectiveToolNames = parameters.toolSpecifications().stream()
                .map(tool -> tool.name())
                .collect(toSet());

        Map<String, ToolSpecification> availableToolsByName = new HashMap<>(availableTools.size());
        availableTools.forEach(tool -> availableToolsByName.put(tool.name(), tool));

        List<ToolSpecification> foundTools = new ArrayList<>();
        for (String foundToolName : foundToolNames) {
            if (effectiveToolNames.contains(foundToolName)) {
                continue;
            }
            ToolSpecification foundTool = availableToolsByName.get(foundToolName);
            if (foundTool == null) {
                throw new IllegalArgumentException("No tool with name '%s' exists".formatted(foundToolName));
            }
            foundTools.add(foundTool);
        }

        return parameters.overrideWith(ChatRequestParameters.builder()
                .toolSpecifications(merge(parameters.toolSpecifications(), foundTools))
                .build());
    }

    private class ToolSearchExecutor implements ToolExecutor {

        private final List<ToolSpecification> searchableTools;

        private ToolSearchExecutor(List<ToolSpecification> searchableTools) {
            this.searchableTools = copy(searchableTools);
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            ToolSearchRequest toolSearchRequest = ToolSearchRequest.builder()
                    .toolExecutionRequest(request)
                    .searchableTools(searchableTools)
                    .invocationContext(context)
                    .build();

            ToolSearchResult toolSearchResult = strategy.search(toolSearchRequest);

            return ToolExecutionResult.builder()
                    .result(toolSearchResult)
                    .resultText(toolSearchResult.toolResultMessageText())
                    .attributes(Map.of(FOUND_TOOLS_ATTRIBUTE, toolSearchResult.foundToolNames()))
                    .build();
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            throw new IllegalStateException("executeWithContext must be called instead");
        }
    }
}
