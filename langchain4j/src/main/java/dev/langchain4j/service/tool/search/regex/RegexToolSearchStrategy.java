package dev.langchain4j.service.tool.search.regex;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;

/**
 * A {@link ToolSearchStrategy} that allows an LLM to search for available tools
 * using a Java regular expression.
 * <p>
 * The strategy exposes a single "tool search tool" to the LLM. When invoked,
 * the LLM provides a regular expression, which is matched (case-insensitively)
 * against tool names and descriptions. Matching tool names are returned,
 * limited by {@link #maxResults}.
 */
public class RegexToolSearchStrategy implements ToolSearchStrategy {

    private static final String DEFAULT_TOOL_NAME = "tool_search_tool_regex";
    private static final String DEFAULT_TOOL_DESCRIPTION = "Finds available tools whose name or description matches a Java regular expression";
    private static final String DEFAULT_TOOL_ARGUMENT_NAME = "regex";
    private static final String DEFAULT_TOOL_ARGUMENT_DESCRIPTION = "A Java regular expression used to search tool names and descriptions (case-insensitive)";
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final ToolSpecification toolSearchTool;
    private final int maxResults;
    private final String toolArgumentName;
    private final boolean throwToolArgumentsExceptions;

    /**
     * Creates a {@link RegexToolSearchStrategy} with default configuration.
     * <p>
     * Defaults include:
     * <ul>
     *     <li>A generic tool name and description</li>
     *     <li>A single required argument named {@code "regex"}</li>
     *     <li>A maximum of 5 results</li>
     *     <li>{@link ToolExecutionException} used for argument-related errors</li>
     * </ul>
     */
    public RegexToolSearchStrategy() {
        this(builder());
    }

    /**
     * Creates a {@link RegexToolSearchStrategy} using the provided {@link Builder}.
     *
     * @param builder the builder containing custom configuration
     */
    public RegexToolSearchStrategy(Builder builder) {
        this.toolArgumentName = getOrDefault(builder.toolArgumentName, DEFAULT_TOOL_ARGUMENT_NAME);
        this.toolSearchTool = ToolSpecification.builder()
                .name(getOrDefault(builder.toolName, DEFAULT_TOOL_NAME))
                .description(getOrDefault(builder.toolDescription, DEFAULT_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(toolArgumentName, getOrDefault(builder.toolArgumentDescription, DEFAULT_TOOL_ARGUMENT_DESCRIPTION))
                        .required(toolArgumentName)
                        .build())
                .build();
        this.maxResults = getOrDefault(builder.maxResults, DEFAULT_MAX_RESULTS);
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
    }

    @Override
    public List<ToolSpecification> toolSearchTools(InvocationContext context) {
        return List.of(toolSearchTool);
    }

    public ToolSearchResult search(ToolSearchRequest request) {
        String regex = extractRegex(request.toolSearchRequest().arguments());
        Pattern pattern = compilePattern(regex);

        List<String> foundToolNames = request.availableTools().stream()
                .filter(tool -> matches(pattern, tool))
                .map(ToolSpecification::name)
                .limit(maxResults)
                .toList();

        return new ToolSearchResult(foundToolNames);
    }

    private String extractRegex(String argumentsJson) {
        Map<String, Object> map = parseMap(argumentsJson);
        if (isNullOrEmpty(map) || !map.containsKey(toolArgumentName)) {
            String message = "Missing required tool argument '%s'".formatted(toolArgumentName);
            if (throwToolArgumentsExceptions) {
                throw new ToolArgumentsException(message);
            } else {
                throw new ToolExecutionException(message);
            }
        }

        return map.get(toolArgumentName).toString();
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            String message = "Failed to parse tool search arguments: '%s' (base64: '%s')".formatted(json, toBase64(json));
            if (throwToolArgumentsExceptions) {
                throw new ToolArgumentsException(message, e);
            } else {
                throw new ToolExecutionException(message, e);
            }
        }
    }

    private Pattern compilePattern(String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            String message = "Failed to compile regex ('%s') into a pattern".formatted(regex);
            if (throwToolArgumentsExceptions) {
                throw new ToolArgumentsException(message, e);
            } else {
                throw new ToolExecutionException(message, e);
            }
        }
    }

    private static boolean matches(Pattern pattern, ToolSpecification tool) {
        return matches(pattern, tool.name()) || matches(pattern, tool.description());
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).find();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer maxResults;
        private String toolName;
        private String toolDescription;
        private String toolArgumentName;
        private String toolArgumentDescription;
        private Boolean throwToolArgumentsExceptions;

        /**
         * Sets the maximum number of tool names that can be returned
         * as a result of a single search.
         * <p>
         * Default: 5
         *
         * @param maxResults maximum number of results
         * @return this builder
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the name of the tool that will be exposed to the LLM
         * for performing tool search.
         * <p>
         * Default: "tool_search_tool_regex"
         *
         * @param toolName the tool name
         * @return this builder
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * Sets the description of the tool that will be exposed to the LLM.
         * This description is visible to the LLM and should clearly explain
         * how the tool is intended to be used.
         * <p>
         * Default: "Finds available tools whose name or description matches a Java regular expression"
         *
         * @param toolDescription the tool description
         * @return this builder
         */
        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        /**
         * Sets the name of the argument that the LLM must provide
         * when invoking the tool search tool.
         * <p>
         * Default: "regex"
         *
         * @param toolArgumentName the argument name
         * @return this builder
         */
        public Builder toolArgumentName(String toolArgumentName) {
            this.toolArgumentName = toolArgumentName;
            return this;
        }

        /**
         * Sets the description of the argument that the LLM must provide.
         * This description is visible to the LLM and should describe
         * the expected regular expression syntax.
         * <p>
         * Default: "A Java regular expression used to search tool names and descriptions (case-insensitive)"
         *
         * @param toolArgumentDescription the argument description
         * @return this builder
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

        public RegexToolSearchStrategy build() {
            return new RegexToolSearchStrategy(this);
        }
    }
}
