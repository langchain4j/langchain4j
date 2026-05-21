package dev.langchain4j.service.tool.search.simple;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparingInt;

/**
 * A {@link ToolSearchStrategy} that allows an LLM to search for available tools
 * using simple case-insensitive {@code contains} matching.
 * <p>
 * The LLM provides a list of search terms. Each term contributes to a tool's score:
 * <ul>
 *     <li>{@code +2} if the tool name contains the term</li>
 *     <li>{@code +1} if the tool description contains the term</li>
 * </ul>
 * <p>
 * Scores are accumulated across all terms. Tools are ranked by score (descending)
 * and limited by {@link #maxResults}.
 */
@Experimental
public class SimpleToolSearchStrategy implements ToolSearchStrategy {

    private static final String DEFAULT_TOOL_NAME = "tool_search_tool";
    private static final String DEFAULT_TOOL_DESCRIPTION = "Finds available tools whose name or description contains given search terms";
    private static final String DEFAULT_TOOL_ARGUMENT_NAME = "terms";
    private static final String DEFAULT_TOOL_ARGUMENT_DESCRIPTION = "A list of individual search terms (single words) used to find relevant tools";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int DEFAULT_MIN_SCORE = 1;
    private static final Function<List<String>, String> DEFAULT_TOOL_RESULT_MESSAGE_TEXT_PROVIDER = foundToolNames -> {
        if (foundToolNames.isEmpty()) {
            return "No matching tools found";
        } else {
            return "Tools found: " + String.join(", ", foundToolNames);
        }
    };

    private final ToolSpecification toolSearchTool;
    private final int maxResults;
    private final int minScore;
    private final String toolArgumentName;
    private final boolean throwToolArgumentsExceptions;
    private final Function<List<String>, String> toolResultMessageTextProvider;

    public SimpleToolSearchStrategy() {
        this(builder());
    }

    public SimpleToolSearchStrategy(Builder builder) {
        this.toolArgumentName = getOrDefault(builder.toolArgumentName, DEFAULT_TOOL_ARGUMENT_NAME);

        this.toolSearchTool = ToolSpecification.builder()
                .name(getOrDefault(builder.toolName, DEFAULT_TOOL_NAME))
                .description(getOrDefault(builder.toolDescription, DEFAULT_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addProperty(toolArgumentName, JsonArraySchema.builder()
                                .description(getOrDefault(builder.toolArgumentDescription, DEFAULT_TOOL_ARGUMENT_DESCRIPTION))
                                .items(new JsonStringSchema())
                                .build())
                        .required(toolArgumentName)
                        .build())
                .build();

        this.maxResults = getOrDefault(builder.maxResults, DEFAULT_MAX_RESULTS);
        this.minScore = getOrDefault(builder.minScore, DEFAULT_MIN_SCORE);
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
        this.toolResultMessageTextProvider = getOrDefault(builder.toolResultMessageTextProvider, DEFAULT_TOOL_RESULT_MESSAGE_TEXT_PROVIDER);
    }

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext context) {
        return List.of(toolSearchTool);
    }

    public ToolSearchResult search(ToolSearchRequest request) {
        List<String> terms = extractTerms(request.toolExecutionRequest().arguments());

        List<ScoredTool> scoredTools = request.searchableTools().stream()
                .map(tool -> new ScoredTool(tool, score(tool, terms)))
                .filter(scoredTool -> scoredTool.score >= minScore)
                .sorted(comparingInt(ScoredTool::score).reversed())
                .limit(maxResults)
                .toList();

        List<String> toolNames = scoredTools.stream()
                .map(st -> st.tool.name())
                .toList();

        String toolResultMessageText = toolResultMessageTextProvider.apply(toolNames);

        return new ToolSearchResult(toolNames, toolResultMessageText);
    }

    protected int score(ToolSpecification tool, List<String> terms) {

        List<String> cleanedTerms = clean(terms);

        String name = lower(tool.name());
        String description = lower(tool.description());

        int score = 0;

        for (String term : cleanedTerms) {
            if (name.contains(term)) {
                score += 2;
            }
            if (description != null && description.contains(term)) {
                score += 1;
            }
        }

        return score;
    }

    protected List<String> clean(List<String> terms) {
        return terms.stream()
                .flatMap(term -> stream(term.split("\\s+")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .toList();
    }

    protected List<String> extractTerms(String argumentsJson) {
        Map<String, Object> map = parseMap(argumentsJson);

        if (isNullOrEmpty(map) || !map.containsKey(toolArgumentName)) {
            String message = "Missing required tool argument '%s'".formatted(toolArgumentName);
            throwArgumentException(message, null);
        }

        Object value = map.get(toolArgumentName);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        } else {
            String message = "Tool argument '%s' must be an array of strings".formatted(toolArgumentName);
            throwArgumentException(message, null);
            return null; // unreachable
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            String message = "Failed to parse tool search arguments: '%s' (base64: '%s')".formatted(json, toBase64(json));
            throwArgumentException(message, e);
            return null; // unreachable
        }
    }

    private void throwArgumentException(String message, Exception e) {
        if (throwToolArgumentsExceptions) {
            if (e == null) throw new ToolArgumentsException(message);
            throw new ToolArgumentsException(message, e);
        } else {
            if (e == null) throw new ToolExecutionException(message);
            throw new ToolExecutionException(message, e);
        }
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private record ScoredTool(ToolSpecification tool, int score) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer maxResults;
        private Integer minScore;
        private String toolName;
        private String toolDescription;
        private String toolArgumentName;
        private String toolArgumentDescription;
        private Boolean throwToolArgumentsExceptions;
        private Function<List<String>, String> toolResultMessageTextProvider;

        /**
         * Sets the maximum number of tools to return after scoring and ranking.
         * <p>
         * Default value is {@value SimpleToolSearchStrategy#DEFAULT_MAX_RESULTS}.
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the minimum score a tool must have to be included in the results.
         * <p>
         * Tools with a score lower than this value are discarded.
         * <p>
         * Default value is {@value SimpleToolSearchStrategy#DEFAULT_MIN_SCORE}.
         */
        public Builder minScore(Integer minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * Sets the name of the tool that performs the tool search.
         * <p>
         * Default value is {@value SimpleToolSearchStrategy#DEFAULT_TOOL_NAME}.
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * Sets the description of the tool that performs the tool search.
         * <p>
         * Default value is {@value SimpleToolSearchStrategy#DEFAULT_TOOL_DESCRIPTION}.
         */
        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        /**
         * Sets the name of the tool argument that contains the list of search terms.
         * <p>
         * Default value is {@value SimpleToolSearchStrategy#DEFAULT_TOOL_ARGUMENT_NAME}.
         */
        public Builder toolArgumentName(String toolArgumentName) {
            this.toolArgumentName = toolArgumentName;
            return this;
        }

        /**
         * Sets the description of the tool argument that contains the list of search terms.
         * <p>
         * Default value is {@value SimpleToolSearchStrategy#DEFAULT_TOOL_ARGUMENT_DESCRIPTION}.
         */
        public Builder toolArgumentDescription(String toolArgumentDescription) {
            this.toolArgumentDescription = toolArgumentDescription;
            return this;
        }

        /**
         * Controls which exception type is thrown when tool arguments
         * are missing, invalid, or cannot be parsed.
         * <p>
         * Although all errors produced by this tool are argument-related,
         * this strategy throws {@link ToolExecutionException} by default
         * instead of {@link ToolArgumentsException}.
         * <p>
         * The reason is historical: by default, AI Services fail fast when
         * a {@link ToolArgumentsException} is thrown, whereas
         * {@link ToolExecutionException} allows the error message to be
         * returned to the LLM. For this tool, returning the error message
         * to the LLM is usually the desired behavior.
         * <p>
         * If this flag is set to {@code true}, {@link ToolArgumentsException}
         * will be thrown instead.
         *
         * @param throwToolArgumentsExceptions whether to throw {@link ToolArgumentsException}
         * @return this builder
         */
        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        /**
         * Sets a function that produces a human-readable message describing
         * the tool search result, based on the list of found tool names.
         * <p>
         * By default, returns:
         * <ul>
         *   <li>{@code "No matching tools found"} when no tools are found</li>
         *   <li>{@code "Tools found: <names>"} otherwise</li>
         * </ul>
         */
        public Builder toolResultMessageTextProvider(Function<List<String>, String> toolResultMessageTextProvider) {
            this.toolResultMessageTextProvider = toolResultMessageTextProvider;
            return this;
        }

        public SimpleToolSearchStrategy build() {
            return new SimpleToolSearchStrategy(this);
        }
    }
}
