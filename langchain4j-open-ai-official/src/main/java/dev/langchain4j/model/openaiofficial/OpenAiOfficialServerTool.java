package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI Responses API server-side tool entry.
 *
 * <p>This class intentionally exposes a small, provider-agnostic LangChain4j surface while the
 * converter layer maps supported tool types into the OpenAI SDK typed {@code Tool} union.
 *
 * <p>Only a fixed set of supported OpenAI Responses server tools is accepted. Unknown tool types are
 * rejected explicitly instead of being passed through as raw JSON. For supported tool types, critical
 * fields have dedicated accessors while non-critical provider-specific fields can still be forwarded via
 * {@link #additionalProperties()}.
 */
@Experimental
public class OpenAiOfficialServerTool {

    private final String type;
    private final String name;
    private final String description;
    private final String searchContextSize;
    private final Map<String, Object> userLocation;
    private final Map<String, Object> filters;
    private final List<String> vectorStoreIds;
    private final Integer maxNumResults;
    private final Map<String, Object> rankingOptions;
    private final String execution;
    private final Object parameters;
    private final String serverLabel;
    private final List<String> allowedTools;
    private final String authorization;
    private final String connectorId;
    private final Boolean deferLoading;
    private final Map<String, Object> headers;
    private final String requireApproval;
    private final String serverDescription;
    private final String serverUrl;
    private final Map<String, Object> environment;
    private final List<Map<String, Object>> tools;
    private final Map<String, Object> additionalProperties;

    public OpenAiOfficialServerTool(Builder builder) {
        this.type = ensureNotBlank(builder.type, "type");
        this.name = builder.name;
        this.description = builder.description;
        this.searchContextSize = builder.searchContextSize;
        this.userLocation = copy(builder.userLocation);
        this.filters = copy(builder.filters);
        this.vectorStoreIds = copy(builder.vectorStoreIds);
        this.maxNumResults = builder.maxNumResults;
        this.rankingOptions = copy(builder.rankingOptions);
        this.execution = builder.execution;
        this.parameters = builder.parameters;
        this.serverLabel = builder.serverLabel;
        this.allowedTools = copy(builder.allowedTools);
        this.authorization = builder.authorization;
        this.connectorId = builder.connectorId;
        this.deferLoading = builder.deferLoading;
        this.headers = copy(builder.headers);
        this.requireApproval = builder.requireApproval;
        this.serverDescription = builder.serverDescription;
        this.serverUrl = builder.serverUrl;
        this.environment = copy(builder.environment);
        this.tools = copy(builder.tools);
        this.additionalProperties = copy(builder.additionalProperties);
    }

    public String type() {
        return type;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String searchContextSize() {
        return searchContextSize;
    }

    public Map<String, Object> userLocation() {
        return userLocation;
    }

    public Map<String, Object> filters() {
        return filters;
    }

    public List<String> vectorStoreIds() {
        return vectorStoreIds;
    }

    public Integer maxNumResults() {
        return maxNumResults;
    }

    public Map<String, Object> rankingOptions() {
        return rankingOptions;
    }

    public String execution() {
        return execution;
    }

    public Object parameters() {
        return parameters;
    }

    public String serverLabel() {
        return serverLabel;
    }

    public List<String> allowedTools() {
        return allowedTools;
    }

    public String authorization() {
        return authorization;
    }

    public String connectorId() {
        return connectorId;
    }

    public Boolean deferLoading() {
        return deferLoading;
    }

    public Map<String, Object> headers() {
        return headers;
    }

    public String requireApproval() {
        return requireApproval;
    }

    public String serverDescription() {
        return serverDescription;
    }

    public String serverUrl() {
        return serverUrl;
    }

    public Map<String, Object> environment() {
        return environment;
    }

    public List<Map<String, Object>> tools() {
        return tools;
    }

    public Map<String, Object> additionalProperties() {
        return additionalProperties;
    }

    /**
     * @deprecated Use {@link #additionalProperties()}.
     */
    @Deprecated
    public Map<String, Object> attributes() {
        return additionalProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiOfficialServerTool that = (OpenAiOfficialServerTool) o;
        return Objects.equals(type, that.type)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(searchContextSize, that.searchContextSize)
                && Objects.equals(userLocation, that.userLocation)
                && Objects.equals(filters, that.filters)
                && Objects.equals(vectorStoreIds, that.vectorStoreIds)
                && Objects.equals(maxNumResults, that.maxNumResults)
                && Objects.equals(rankingOptions, that.rankingOptions)
                && Objects.equals(execution, that.execution)
                && Objects.equals(parameters, that.parameters)
                && Objects.equals(serverLabel, that.serverLabel)
                && Objects.equals(allowedTools, that.allowedTools)
                && Objects.equals(authorization, that.authorization)
                && Objects.equals(connectorId, that.connectorId)
                && Objects.equals(deferLoading, that.deferLoading)
                && Objects.equals(headers, that.headers)
                && Objects.equals(requireApproval, that.requireApproval)
                && Objects.equals(serverDescription, that.serverDescription)
                && Objects.equals(serverUrl, that.serverUrl)
                && Objects.equals(environment, that.environment)
                && Objects.equals(tools, that.tools)
                && Objects.equals(additionalProperties, that.additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                type,
                name,
                description,
                searchContextSize,
                userLocation,
                filters,
                vectorStoreIds,
                maxNumResults,
                rankingOptions,
                execution,
                parameters,
                serverLabel,
                allowedTools,
                authorization,
                connectorId,
                deferLoading,
                headers,
                requireApproval,
                serverDescription,
                serverUrl,
                environment,
                tools,
                additionalProperties);
    }

    @Override
    public String toString() {
        return "OpenAiOfficialServerTool{" + "type='"
                + type + '\'' + ", name='"
                + name + '\'' + ", attributes="
                + additionalProperties + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String type;
        private String name;
        private String description;
        private String searchContextSize;
        private Map<String, Object> userLocation;
        private Map<String, Object> filters;
        private List<String> vectorStoreIds;
        private Integer maxNumResults;
        private Map<String, Object> rankingOptions;
        private String execution;
        private Object parameters;
        private String serverLabel;
        private List<String> allowedTools;
        private String authorization;
        private String connectorId;
        private Boolean deferLoading;
        private Map<String, Object> headers;
        private String requireApproval;
        private String serverDescription;
        private String serverUrl;
        private Map<String, Object> environment;
        private List<Map<String, Object>> tools;
        private Map<String, Object> additionalProperties;

        /**
         * Sets the OpenAI tool type, for example {@code web_search}, {@code file_search},
         * {@code tool_search}, or {@code mcp}.
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the optional tool name.
         *
         * <p>Some OpenAI built-in tools do not require a name.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder searchContextSize(String searchContextSize) {
            this.searchContextSize = searchContextSize;
            return this;
        }

        public Builder userLocation(Map<String, Object> userLocation) {
            this.userLocation = userLocation;
            return this;
        }

        public Builder filters(Map<String, Object> filters) {
            this.filters = filters;
            return this;
        }

        public Builder vectorStoreIds(List<String> vectorStoreIds) {
            this.vectorStoreIds = vectorStoreIds;
            return this;
        }

        public Builder maxNumResults(Integer maxNumResults) {
            this.maxNumResults = maxNumResults;
            return this;
        }

        public Builder rankingOptions(Map<String, Object> rankingOptions) {
            this.rankingOptions = rankingOptions;
            return this;
        }

        public Builder execution(String execution) {
            this.execution = execution;
            return this;
        }

        public Builder parameters(Object parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder serverLabel(String serverLabel) {
            this.serverLabel = serverLabel;
            return this;
        }

        public Builder allowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools;
            return this;
        }

        public Builder authorization(String authorization) {
            this.authorization = authorization;
            return this;
        }

        public Builder connectorId(String connectorId) {
            this.connectorId = connectorId;
            return this;
        }

        public Builder deferLoading(Boolean deferLoading) {
            this.deferLoading = deferLoading;
            return this;
        }

        public Builder headers(Map<String, Object> headers) {
            this.headers = headers;
            return this;
        }

        public Builder requireApproval(String requireApproval) {
            this.requireApproval = requireApproval;
            return this;
        }

        public Builder serverDescription(String serverDescription) {
            this.serverDescription = serverDescription;
            return this;
        }

        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder environment(Map<String, Object> environment) {
            this.environment = environment;
            return this;
        }

        public Builder tools(List<Map<String, Object>> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Replaces non-critical provider-specific fields that should be forwarded as-is.
         */
        public Builder additionalProperties(Map<String, Object> additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }

        /**
         * @deprecated Use {@link #additionalProperties(Map)}.
         */
        @Deprecated
        public Builder attributes(Map<String, Object> attributes) {
            return additionalProperties(attributes);
        }

        /**
         * Adds a provider-specific field serialized into the tool entry.
         */
        public Builder addAdditionalProperty(String key, Object value) {
            if (this.additionalProperties == null) {
                this.additionalProperties = new LinkedHashMap<>();
            }
            this.additionalProperties.put(key, value);
            return this;
        }

        /**
         * @deprecated Use {@link #addAdditionalProperty(String, Object)}.
         */
        @Deprecated
        public Builder addAttribute(String key, Object value) {
            return addAdditionalProperty(key, value);
        }

        public OpenAiOfficialServerTool build() {
            return new OpenAiOfficialServerTool(this);
        }
    }
}
