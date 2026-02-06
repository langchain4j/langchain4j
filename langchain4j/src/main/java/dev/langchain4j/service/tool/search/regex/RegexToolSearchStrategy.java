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

/**
 * TODO
 */
public class RegexToolSearchStrategy implements ToolSearchStrategy {

    private static final String DEFAULT_TOOL_NAME = "tool_search_tool_regex"; // TODO
    private static final String DEFAULT_TOOL_DESCRIPTION = "Searches for tools using regular expressions"; // TODO
    private static final String DEFAULT_TOOL_ARGUMENT_NAME = "regex"; // TODO
    private static final String DEFAULT_TOOL_ARGUMENT_DESCRIPTION = "Regular expression using Java syntax"; // TODO
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final ToolSpecification toolSearchTool;
    private final int maxResults;
    private final String toolArgumentName;
    private final boolean throwToolArgumentsExceptions;

    /**
     * TODO
     */
    public RegexToolSearchStrategy() {
        this(builder());
    }

    /**
     * TODO
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
            // TODO include base64
            String message = "Failed to parse tool search arguments: '%s'".formatted(json);
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
         * TODO
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * TODO
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * TODO
         */
        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        /**
         * TODO
         */
        public Builder toolArgumentName(String toolArgumentName) {
            this.toolArgumentName = toolArgumentName;
            return this;
        }

        /**
         * TODO
         */
        public Builder toolArgumentDescription(String toolArgumentDescription) {
            this.toolArgumentDescription = toolArgumentDescription;
            return this;
        }

        /**
         * TODO
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
