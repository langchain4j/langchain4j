package dev.langchain4j.service.tool.search;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolServiceContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE;
import static dev.langchain4j.agent.tool.SearchBehavior.NOT_SEARCHABLE;
import static dev.langchain4j.agent.tool.ToolSpecification.METADATA_SEARCH_BEHAVIOR;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.merge;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.tool.ToolService.addTools;
import static dev.langchain4j.service.tool.ToolService.findTools;

/**
 * @since 1.12.0
 */
@Internal
public class ToolSearchService {

    public static final String FOUND_TOOLS_ATTRIBUTE = "found_tools"; // do not change, will break backward compatibility!

    private final ToolSearchStrategy strategy;

    public ToolSearchService(ToolSearchStrategy toolSearchStrategy) {
        this.strategy = ensureNotNull(toolSearchStrategy, "toolSearchStrategy");
    }

    public ToolServiceContext adjust(ToolServiceContext toolServiceContext,
                                     List<ChatMessage> messages,
                                     InvocationContext invocationContext) {
        List<ToolSpecification> toolSearchTools = strategy.getToolSearchTools(invocationContext);
        List<ToolSpecification> availableTools = toolServiceContext.availableTools();
        List<ToolSpecification> effectiveTools = calculateEffectiveTools(toolSearchTools, availableTools, messages);
        List<ToolSpecification> searchableTools = calculateSearchableTools(availableTools, effectiveTools);
        Map<String, ToolExecutor> toolSearchToolExecutors = createExecutors(toolSearchTools, searchableTools);
        return toolServiceContext.toBuilder()
                .effectiveTools(effectiveTools)
                .toolExecutors(merge(toolServiceContext.toolExecutors(), toolSearchToolExecutors))
                .build();
    }

    /**
     * @deprecated use {@link #adjust(ToolServiceContext, List, InvocationContext)} instead
     */
    @Deprecated(since = "1.13.0")
    public ToolServiceContext adjust(ToolServiceContext toolServiceContext,
                                     ChatMemory chatMemory,
                                     InvocationContext invocationContext) {
        return adjust(toolServiceContext, chatMemory.messages(), invocationContext);
    }

    private List<ToolSpecification> calculateEffectiveTools(List<ToolSpecification> toolSearchTools,
                                                            List<ToolSpecification> availableTools,
                                                            List<ChatMessage> messages) {
        List<ToolSpecification> effectiveTools = new ArrayList<>();

        availableTools.forEach(tool -> {
            if (tool.metadata().get(METADATA_SEARCH_BEHAVIOR) == ALWAYS_VISIBLE) {
                effectiveTools.add(tool);
            }
        });

        effectiveTools.addAll(toolSearchTools);
        effectiveTools.addAll(findTools(messages, Set.of(FOUND_TOOLS_ATTRIBUTE), availableTools, effectiveTools));

        return effectiveTools;
    }

    private List<ToolSpecification> calculateSearchableTools(List<ToolSpecification> availableTools,
                                                             List<ToolSpecification> effectiveTools) {
        Set<ToolSpecification> searchableTools = new LinkedHashSet<>(availableTools);
        searchableTools.removeAll(effectiveTools);
        searchableTools.removeIf(tool -> tool.metadata().get(METADATA_SEARCH_BEHAVIOR) == NOT_SEARCHABLE);
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

    /**
     * @deprecated use {@link ToolService#addTools} instead
     */
    @Deprecated(since = "1.13.0")
    public static ChatRequestParameters addFoundTools(ChatRequestParameters parameters,
                                                      Collection<ToolExecutionResult> toolResults,
                                                      List<ToolSpecification> availableTools) {
        return addTools(parameters, toolResults, availableTools, Set.of(FOUND_TOOLS_ATTRIBUTE));
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
