package dev.langchain4j.service.tool.search.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link ToolProvider} that passes on only the tools of another tool provider that are relevant to the user
 * message, as decided by a {@link DecisionModel}. This keeps requests to the LLM small when a tool provider offers
 * many tools, for example an MCP server:
 * <pre>{@code
 * Assistant assistant = AiServices.builder(Assistant.class)
 *         .chatModel(chatModel)
 *         .toolProvider(DecisionModelToolProvider.builder()
 *                 .toolProvider(mcpToolProvider)
 *                 .decisionModel(decisionModel)
 *                 .maxResults(5)
 *                 .build())
 *         .build();
 * }</pre>
 * Unlike a {@link dev.langchain4j.service.tool.search.ToolSearchStrategy}, the tools are selected before the first
 * LLM call, so no tool search round trip is needed, but the selection only depends on the user message.
 * If the user message has no text, all tools are passed on.
 *
 * @see DecisionModelToolSearchStrategy
 * @since 1.21.0
 */
@Experimental
public class DecisionModelToolProvider implements ToolProvider {

    private final ToolProvider toolProvider;
    private final DecisionModelToolSelector selector;

    protected DecisionModelToolProvider(Builder builder) {
        this.toolProvider = ensureNotNull(builder.toolProvider, "toolProvider");
        this.selector = new DecisionModelToolSelector(
                builder.decisionModel,
                builder.question,
                builder.maxResults,
                builder.minProbability,
                builder.maxToolsPerRequest);
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult result = toolProvider.provideTools(request);
        String userMessage = text(request.userMessage());
        if (result == null || result.aiServiceTools().isEmpty() || userMessage.isBlank()) {
            return result;
        }
        List<ToolSpecification> tools = result.aiServiceTools().stream()
                .map(AiServiceTool::toolSpecification)
                .toList();
        Set<String> selected = new HashSet<>(selector.select(userMessage, tools));
        return new ToolProviderResult(result.aiServiceTools().stream()
                .filter(tool -> selected.contains(tool.name()))
                .toList());
    }

    @Override
    public boolean isDynamic() {
        return toolProvider.isDynamic();
    }

    private static String text(UserMessage userMessage) {
        if (userMessage == null) {
            return "";
        }
        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(joining("\n"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolProvider toolProvider;
        private DecisionModel decisionModel;
        private String question;
        private Integer maxResults;
        private Double minProbability;
        private Integer maxToolsPerRequest;

        /**
         * Sets the tool provider whose tools are filtered. Required.
         */
        public Builder toolProvider(ToolProvider toolProvider) {
            this.toolProvider = toolProvider;
            return this;
        }

        /**
         * Sets the decision model that decides which tools are relevant. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Sets the yes/no question asked for each tool, followed by the name and description of the tool.
         * <p>
         * Default value is "Would this tool help to handle the request?"
         */
        public Builder question(String question) {
            this.question = question;
            return this;
        }

        /**
         * Sets the maximum number of tools passed on.
         * <p>
         * Default value is 5.
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the minimum probability of "yes" for a tool to be passed on.
         * <p>
         * Default value is 0.5.
         */
        public Builder minProbability(Double minProbability) {
            this.minProbability = minProbability;
            return this;
        }

        /**
         * Sets the maximum number of tools evaluated in a single request to the decision model.
         * <p>
         * Default value is 50.
         */
        public Builder maxToolsPerRequest(Integer maxToolsPerRequest) {
            this.maxToolsPerRequest = maxToolsPerRequest;
            return this;
        }

        public DecisionModelToolProvider build() {
            return new DecisionModelToolProvider(this);
        }
    }
}
