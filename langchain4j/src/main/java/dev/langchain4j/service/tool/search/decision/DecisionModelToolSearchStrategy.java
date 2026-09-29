package dev.langchain4j.service.tool.search.decision;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A {@link ToolSearchStrategy} that uses a {@link DecisionModel} to find the tools matching the search query of the
 * LLM: for each searchable tool, the decision model answers whether the tool would help with the query (all tools in
 * a single call), and the most relevant tools are returned.
 * <pre>{@code
 * Assistant assistant = AiServices.builder(Assistant.class)
 *         .chatModel(chatModel)
 *         .toolProvider(mcpToolProvider)
 *         .toolSearchStrategy(new DecisionModelToolSearchStrategy(decisionModel))
 *         .build();
 * }</pre>
 * Unlike an embedding-based search, the decision model reads the descriptions of the tools and the query together,
 * so it can find tools that match by meaning rather than by wording. If the decision model fails, the exception is
 * propagated to the tool-calling loop like the error of any tool.
 *
 * @see dev.langchain4j.service.tool.DecisionModelFilteringToolProvider to select tools before the first LLM call, without a tool search round trip
 * @since 1.21.0
 */
@Experimental
public class DecisionModelToolSearchStrategy implements ToolSearchStrategy {

    /**
     * The default template of the question asked for each tool:
     * {@code "Would this tool help to handle the request?\nTool: {{name}}\nDescription: {{description}}"}.
     */
    public static final PromptTemplate DEFAULT_QUESTION_TEMPLATE = DecisionModelToolSelector.DEFAULT_QUESTION_TEMPLATE;


    private static final String DEFAULT_TOOL_NAME = "tool_search_tool";
    private static final String DEFAULT_TOOL_DESCRIPTION = "Finds available tools that can help with a task";
    private static final String DEFAULT_TOOL_ARGUMENT_NAME = "query";
    private static final String DEFAULT_TOOL_ARGUMENT_DESCRIPTION = "Natural language description of the task";
    private static final Function<List<String>, String> DEFAULT_TOOL_RESULT_MESSAGE_TEXT_PROVIDER = foundToolNames -> {
        if (foundToolNames.isEmpty()) {
            return "No matching tools found";
        } else {
            return "Tools found: " + String.join(", ", foundToolNames);
        }
    };

    private final ToolSpecification toolSearchTool;
    private final String toolArgumentName;
    private final DecisionModelToolSelector selector;
    private final boolean throwToolArgumentsExceptions;
    private final Function<List<String>, String> toolResultMessageTextProvider;

    public DecisionModelToolSearchStrategy(DecisionModel decisionModel) {
        this(builder().decisionModel(decisionModel));
    }

    protected DecisionModelToolSearchStrategy(Builder builder) {
        this.selector = new DecisionModelToolSelector(
                builder.decisionModel,
                builder.questionTemplate,
                builder.maxResults,
                builder.minProbability,
                builder.maxToolsPerDecisionRequest);
        this.toolArgumentName = getOrDefault(builder.toolArgumentName, DEFAULT_TOOL_ARGUMENT_NAME);
        this.toolSearchTool = ToolSpecification.builder()
                .name(getOrDefault(builder.toolName, DEFAULT_TOOL_NAME))
                .description(getOrDefault(builder.toolDescription, DEFAULT_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(
                                toolArgumentName,
                                getOrDefault(builder.toolArgumentDescription, DEFAULT_TOOL_ARGUMENT_DESCRIPTION))
                        .required(toolArgumentName)
                        .build())
                .build();
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
        this.toolResultMessageTextProvider =
                getOrDefault(builder.toolResultMessageTextProvider, DEFAULT_TOOL_RESULT_MESSAGE_TEXT_PROVIDER);
    }

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext invocationContext) {
        return List.of(toolSearchTool);
    }

    @Override
    public ToolSearchResult search(ToolSearchRequest request) {
        String query = extractQuery(request.toolExecutionRequest().arguments());
        List<String> toolNames = request.searchableTools().isEmpty()
                ? List.of()
                : selector.select(query, request.searchableTools());
        return new ToolSearchResult(toolNames, toolResultMessageTextProvider.apply(toolNames));
    }

    private String extractQuery(String argumentsJson) {
        Map<?, ?> arguments;
        try {
            arguments = Json.fromJson(argumentsJson, Map.class);
        } catch (Exception e) {
            throw exception(
                    "Failed to parse tool search arguments: '%s' (base64: '%s')"
                            .formatted(argumentsJson, toBase64(argumentsJson)),
                    e);
        }
        Object query = isNullOrEmpty(arguments) ? null : arguments.get(toolArgumentName);
        if (query == null || isNullOrBlank(query.toString())) {
            throw exception("Missing required tool argument '%s'".formatted(toolArgumentName), null);
        }
        return query.toString();
    }

    private RuntimeException exception(String message, Exception cause) {
        if (throwToolArgumentsExceptions) {
            return cause == null ? new ToolArgumentsException(message) : new ToolArgumentsException(message, cause);
        }
        return cause == null ? new ToolExecutionException(message) : new ToolExecutionException(message, cause);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DecisionModel decisionModel;
        private PromptTemplate questionTemplate;
        private Integer maxResults;
        private Double minProbability;
        private Integer maxToolsPerDecisionRequest;
        private String toolName;
        private String toolDescription;
        private String toolArgumentName;
        private String toolArgumentDescription;
        private Boolean throwToolArgumentsExceptions;
        private Function<List<String>, String> toolResultMessageTextProvider;

        /**
         * Sets the decision model that decides which tools match the query. Required.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Sets the template of the yes/no question asked for each tool, which must contain the {@code {{name}}}
         * variable (the name of the tool) and can contain {@code {{description}}} (its description, empty if it has
         * none).
         * <p>
         * Default value is {@link DecisionModelToolSearchStrategy#DEFAULT_QUESTION_TEMPLATE}.
         */
        public Builder questionTemplate(PromptTemplate questionTemplate) {
            this.questionTemplate = questionTemplate;
            return this;
        }

        /**
         * Sets the maximum number of tools returned by a search.
         * <p>
         * Default value is 5.
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the minimum probability of "yes" for a tool to be returned.
         * <p>
         * Default value is 0.5.
         */
        public Builder minProbability(Double minProbability) {
            this.minProbability = minProbability;
            return this;
        }

        /**
         * Sets the maximum number of tools evaluated in a single request to the decision model, for when the tools
         * together exceed the input size accepted by the decision model.
         * <p>
         * By default, all tools are evaluated in a single request. This does not limit the number of tools passed on
         * to the LLM, see {@link #maxResults(Integer)}.
         */
        public Builder maxToolsPerDecisionRequest(Integer maxToolsPerDecisionRequest) {
            this.maxToolsPerDecisionRequest = maxToolsPerDecisionRequest;
            return this;
        }

        /**
         * Sets the name of the tool that performs the tool search.
         * <p>
         * Default value is {@value DecisionModelToolSearchStrategy#DEFAULT_TOOL_NAME}.
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * Sets the description of the tool that performs the tool search.
         * <p>
         * Default value is {@value DecisionModelToolSearchStrategy#DEFAULT_TOOL_DESCRIPTION}.
         */
        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        /**
         * Sets the name of the tool argument that contains the search query.
         * <p>
         * Default value is {@value DecisionModelToolSearchStrategy#DEFAULT_TOOL_ARGUMENT_NAME}.
         */
        public Builder toolArgumentName(String toolArgumentName) {
            this.toolArgumentName = toolArgumentName;
            return this;
        }

        /**
         * Sets the description of the tool argument that contains the search query.
         * <p>
         * Default value is {@value DecisionModelToolSearchStrategy#DEFAULT_TOOL_ARGUMENT_DESCRIPTION}.
         */
        public Builder toolArgumentDescription(String toolArgumentDescription) {
            this.toolArgumentDescription = toolArgumentDescription;
            return this;
        }

        /**
         * Controls which exception type is thrown when the tool arguments are missing or cannot be parsed:
         * {@link ToolArgumentsException} if {@code true}, {@link ToolExecutionException} otherwise.
         * <p>
         * Default value is {@code false}.
         */
        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        /**
         * Sets the function that creates the text of the tool result message from the names of the found tools.
         * <p>
         * By default, the text lists the names of the found tools, or says that no matching tools were found.
         */
        public Builder toolResultMessageTextProvider(Function<List<String>, String> toolResultMessageTextProvider) {
            this.toolResultMessageTextProvider = toolResultMessageTextProvider;
            return this;
        }

        public DecisionModelToolSearchStrategy build() {
            return new DecisionModelToolSearchStrategy(this);
        }
    }
}
