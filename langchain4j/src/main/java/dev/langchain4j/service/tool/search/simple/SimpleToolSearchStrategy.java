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

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;
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
    private static final String DEFAULT_TOOL_ARGUMENT_DESCRIPTION = "A list of search terms used to find relevant tools";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int DEFAULT_MIN_SCORE = 1;

    private final ToolSpecification toolSearchTool;
    private final int maxResults;
    private final int minScore;
    private final String toolArgumentName;
    private final boolean throwToolArgumentsExceptions;

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
    }

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext context) {
        return List.of(toolSearchTool);
    }

    public ToolSearchResult search(ToolSearchRequest request) {
        List<String> terms = extractTerms(request.toolExecutionRequest().arguments());

        List<ScoredTool> scoredTools = request.availableTools().stream()
                .map(tool -> new ScoredTool(tool, score(tool, terms)))
                .filter(scoredTool -> scoredTool.score >= minScore)
                .sorted(comparingInt(ScoredTool::score).reversed())
                .limit(maxResults)
                .toList();

        List<String> toolNames = scoredTools.stream()
                .map(st -> st.tool.name())
                .toList();

        return new ToolSearchResult(toolNames);
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

        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder minScore(Integer minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        public Builder toolArgumentName(String toolArgumentName) {
            this.toolArgumentName = toolArgumentName;
            return this;
        }

        public Builder toolArgumentDescription(String toolArgumentDescription) {
            this.toolArgumentDescription = toolArgumentDescription;
            return this;
        }

        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        public SimpleToolSearchStrategy build() {
            return new SimpleToolSearchStrategy(this);
        }
    }
}
