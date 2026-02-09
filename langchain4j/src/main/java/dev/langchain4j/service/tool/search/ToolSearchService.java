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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toSet;

@Internal
public class ToolSearchService {

    private static final String FOUND_TOOLS_ATTRIBUTE = "found_tools"; // do not change, will break backward compatibility!

    private final ToolSearchStrategy strategy;

    public ToolSearchService(ToolSearchStrategy toolSearchStrategy) {
        this.strategy = ensureNotNull(toolSearchStrategy, "toolSearchStrategy");
    }

    public List<ToolSpecification> toolSearchTools(InvocationContext invocationContext) {
        return strategy.toolSearchTools(invocationContext);
    }

    // TODO refactor
    public List<ToolSpecification> getEffectiveTools(ChatMemory chatMemory,
                                                     List<ToolSpecification> availableTools,
                                                     List<ToolSpecification> toolSearchTools) { // TODO name
        if (this.strategy == null) {
            return availableTools;
        }

        if (chatMemory == null) {
            return toolSearchTools;
        }

        List<ToolSpecification> result = new ArrayList<>(toolSearchTools);
        Set<String> previouslyFoundToolNames = chatMemory.messages().stream()
                .filter(it -> it instanceof ToolExecutionResultMessage)
                .map(it -> (ToolExecutionResultMessage) it)
                .map(it -> it.attributes().get(FOUND_TOOLS_ATTRIBUTE))
                .filter(it -> it != null)
                .map(it -> (List<String>) it)
                .flatMap(List::stream)
                .collect(toSet());// TODO unit test, optimize
        if (!previouslyFoundToolNames.isEmpty()) {
            Map<String, ToolSpecification> toolSpecsByName = new HashMap<>(availableTools.size());
            availableTools.forEach(spec -> toolSpecsByName.put(spec.name(), spec));
            previouslyFoundToolNames.forEach(toolName -> result.add(toolSpecsByName.get(toolName)));
        }
        return result;
    }

    // TODO refactor
    public Map<String, ToolExecutor> getToolExecutors(Map<String, ToolExecutor> toolExecutors,
                                                      List<ToolSpecification> availableTools,
                                                      List<ToolSpecification> toolSearchTools) {
        if (this.strategy == null) {
            return toolExecutors;
        }

        Map<String, ToolExecutor> result = new HashMap<>(toolExecutors);
        for (ToolSpecification toolSearchTool : toolSearchTools) {
            ToolExecutor toolExecutor = new ToolSearchExecutor(availableTools);
            result.put(toolSearchTool.name(), toolExecutor);
        }
        return result;
    }

    // TODO refactor
    public static ChatRequestParameters addNewFoundTools(ChatRequestParameters parameters,
                                                         Collection<ToolExecutionResult> toolResults,
                                                         List<ToolSpecification> availableTools) {
        Set<String> newFoundToolNames = new LinkedHashSet<>();
        for (ToolExecutionResult toolResult : toolResults) {
            Map<String, Object> attributes = toolResult.attributes();
            if (attributes.containsKey(FOUND_TOOLS_ATTRIBUTE)) {
                newFoundToolNames.addAll((List<String>) attributes.get(FOUND_TOOLS_ATTRIBUTE));
            }
        }
        List<ToolSpecification> newFoundTools = new ArrayList<>();
        for (String foundToolName : newFoundToolNames) {
            // TODO optimize
            if (parameters.toolSpecifications().stream().anyMatch(it -> foundToolName.equals(it.name()))) {
                // this tool was already found previously
                continue;
            }
            ToolSpecification foundTool = availableTools.stream()
                    .filter(it -> it.name().equals(foundToolName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No tool with name '%s' exists".formatted(foundToolName)));
            newFoundTools.add(foundTool);
        }
        if (!newFoundTools.isEmpty()) {
            List<ToolSpecification> allTools = new ArrayList<>();
            allTools.addAll(parameters.toolSpecifications());
            allTools.addAll(newFoundTools);
            parameters = parameters.overrideWith(ChatRequestParameters.builder()
                    .toolSpecifications(allTools)
                    .build());
        }
        return parameters;
    }

    private class ToolSearchExecutor implements ToolExecutor {

        private final List<ToolSpecification> availableTools;

        public ToolSearchExecutor(List<ToolSpecification> availableTools) {
            this.availableTools = copy(availableTools);
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            ToolSearchRequest toolSearchRequest = ToolSearchRequest.builder()
                    .toolSearchRequest(request)
                    .availableTools(availableTools)
                    .invocationContext(context)
                    .build();

            ToolSearchResult toolSearchResult = strategy.search(toolSearchRequest);

            return ToolExecutionResult.builder()
                    .result(toolSearchResult)
                    .resultText(toolSearchResult.toolExecutionResultMessageText())
                    .attributes(Map.of(FOUND_TOOLS_ATTRIBUTE, toolSearchResult.foundToolNames()))
                    .build();
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            throw new IllegalStateException("executeWithContext must be called instead");
        }
    }
}
