package dev.langchain4j.service.tool.search;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.merge;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolServiceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @since 1.12.0
 */
@Internal
public class ToolSearchService {

    private static final String FOUND_TOOLS_ATTRIBUTE =
            "found_tools"; // do not change, will break backward compatibility!

    private final ToolSearchStrategy strategy;

    public ToolSearchService(ToolSearchStrategy toolSearchStrategy) {
        this.strategy = ensureNotNull(toolSearchStrategy, "toolSearchStrategy");
    }

    public ToolServiceContext adjust(
            ToolServiceContext toolServiceContext, List<ChatMessage> messages, InvocationContext invocationContext) {
        List<ToolSpecification> toolSearchTools = strategy.getToolSearchTools(invocationContext);
        List<ToolSpecification> availableTools = toolServiceContext.availableTools();
        List<ToolSpecification> effectiveTools = calculateEffectiveTools(toolSearchTools, availableTools, messages);
        List<ToolSpecification> searchableTools = calculateSearchableTools(availableTools, effectiveTools, Set.of());
        Map<String, ToolExecutor> toolSearchToolExecutors = createExecutors(toolSearchTools, searchableTools);
        return toolServiceContext.toBuilder()
                .effectiveTools(effectiveTools)
                .toolExecutors(merge(toolServiceContext.toolExecutors(), toolSearchToolExecutors))
                .build();
    }

    /**
     * Rebuilds only the {@link ToolSearchExecutor}s with a fresh {@code searchableTools} snapshot
     * that excludes {@code exhaustedToolNames}. Does not touch {@code effectiveTools} or
     * re-walk message history, so it can be safely called per loop iteration without demoting
     * dynamic-provider tools that were added after {@link #adjust} ran at context creation.
     *
     * @since 1.14.0
     */
    public ToolServiceContext refreshSearchExecutors(
            ToolServiceContext toolServiceContext,
            InvocationContext invocationContext,
            Set<String> exhaustedToolNames) {
        List<ToolSpecification> toolSearchTools = strategy.getToolSearchTools(invocationContext);
        List<ToolSpecification> searchableTools = calculateSearchableTools(
                toolServiceContext.availableTools(), toolServiceContext.effectiveTools(), exhaustedToolNames);
        Map<String, ToolExecutor> freshSearchExecutors = createExecutors(toolSearchTools, searchableTools);
        // Replace (not merge) the existing ToolSearchExecutor entries so the stale snapshot is
        // dropped in favor of the fresh one. Non-search executors are preserved.
        Map<String, ToolExecutor> updatedExecutors = new HashMap<>(toolServiceContext.toolExecutors());
        updatedExecutors.putAll(freshSearchExecutors);
        return toolServiceContext.toBuilder().toolExecutors(updatedExecutors).build();
    }

    private List<ToolSpecification> calculateEffectiveTools(
            List<ToolSpecification> toolSearchTools,
            List<ToolSpecification> availableTools,
            List<ChatMessage> messages) {
        List<ToolSpecification> effectiveTools = new ArrayList<>();

        availableTools.forEach(tool -> {
            if (tool.metadata().get(METADATA_SEARCH_BEHAVIOR) == ALWAYS_VISIBLE) {
                effectiveTools.add(tool);
            }
        });

        effectiveTools.addAll(toolSearchTools);

        if (isNullOrEmpty(messages)) {
            return effectiveTools;
        }

        Set<String> toolNamesFoundEarlier = messages.stream()
                .filter(it -> it instanceof ToolExecutionResultMessage)
                .map(it -> (ToolExecutionResultMessage) it)
                .map(it -> it.attributes().get(FOUND_TOOLS_ATTRIBUTE))
                .filter(Objects::nonNull)
                .map(it -> (List<String>) it)
                .flatMap(List::stream)
                .collect(toCollection(LinkedHashSet::new));

        if (toolNamesFoundEarlier.isEmpty()) {
            return effectiveTools;
        }

        Map<String, ToolSpecification> toolsByName = new HashMap<>(availableTools.size());
        availableTools.forEach(tool -> toolsByName.put(tool.name(), tool));
        toolNamesFoundEarlier.forEach(toolName -> effectiveTools.add(toolsByName.get(toolName)));

        return effectiveTools;
    }

    private List<ToolSpecification> calculateSearchableTools(
            List<ToolSpecification> availableTools,
            List<ToolSpecification> effectiveTools,
            Set<String> exhaustedToolNames) {
        Set<ToolSpecification> searchableTools = new LinkedHashSet<>(availableTools);
        searchableTools.removeAll(effectiveTools);
        if (!exhaustedToolNames.isEmpty()) {
            searchableTools.removeIf(tool -> exhaustedToolNames.contains(tool.name()));
        }
        return new ArrayList<>(searchableTools);
    }

    private Map<String, ToolExecutor> createExecutors(
            List<ToolSpecification> toolSearchTools, List<ToolSpecification> searchableTools) {
        Map<String, ToolExecutor> executors = new HashMap<>();
        for (ToolSpecification toolSearchTool : toolSearchTools) {
            executors.put(toolSearchTool.name(), new ToolSearchExecutor(searchableTools));
        }
        return executors;
    }

    public static ToolServiceContext addFoundTools(
            ToolServiceContext toolServiceContext, Collection<ToolExecutionResult> toolResults) {
        return addFoundTools(toolServiceContext, toolResults, Set.of());
    }

    /**
     * @param exhaustedToolNames names of tools whose invocation limits have been reached; any
     *                           found-tool names matching this set are skipped before the
     *                           availableTools lookup, so exhaustion does not trigger the
     *                           "unknown tool" throw below and does not re-add the tool to
     *                           {@code effectiveTools}.
     * @since 1.14.0
     */
    public static ToolServiceContext addFoundTools(
            ToolServiceContext toolServiceContext,
            Collection<ToolExecutionResult> toolResults,
            Set<String> exhaustedToolNames) {
        Set<String> foundToolNames = new LinkedHashSet<>();
        for (ToolExecutionResult toolResult : toolResults) {
            Object attribute = toolResult.attributes().get(FOUND_TOOLS_ATTRIBUTE);
            if (attribute instanceof List<?> foundToolNamesList) {
                foundToolNames.addAll((List<String>) foundToolNamesList);
            }
        }
        if (!exhaustedToolNames.isEmpty()) {
            foundToolNames.removeAll(exhaustedToolNames);
        }
        if (foundToolNames.isEmpty()) {
            return toolServiceContext;
        }

        Set<String> effectiveToolNames = toolServiceContext.effectiveTools().stream()
                .map(ToolSpecification::name)
                .collect(toSet());

        Map<String, ToolSpecification> availableToolsByName =
                new HashMap<>(toolServiceContext.availableTools().size());
        toolServiceContext.availableTools().forEach(tool -> availableToolsByName.put(tool.name(), tool));

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

        if (foundTools.isEmpty()) {
            return toolServiceContext;
        }

        return toolServiceContext.toBuilder()
                .effectiveTools(merge(toolServiceContext.effectiveTools(), foundTools))
                .build();
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
