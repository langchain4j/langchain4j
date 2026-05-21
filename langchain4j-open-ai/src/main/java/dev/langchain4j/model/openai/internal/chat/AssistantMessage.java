package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.model.openai.internal.chat.Role.ASSISTANT;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

@JsonDeserialize(builder = AssistantMessage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class AssistantMessage implements Message {

    @JsonProperty
    private final Role role = ASSISTANT;
    @JsonProperty
    private final String content;
    @JsonProperty
    private final String reasoningContent;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final List<ToolCall> toolCalls;
    @JsonProperty
    private final String refusal;
    @JsonProperty
    @Deprecated
    private final FunctionCall functionCall;
    @JsonIgnore
    private final Map<String, Object> customParameters;

    public AssistantMessage(Builder builder) {
        this.content = builder.content;
        this.reasoningContent = builder.reasoningContent;
        this.name = builder.name;
        this.toolCalls = builder.toolCalls;
        this.refusal = builder.refusal;
        this.functionCall = builder.functionCall;
        this.customParameters = builder.customParameters;
    }

    public Role role() {
        return role;
    }

    public String content() {
        return content;
    }

    public String reasoningContent() {
        return reasoningContent;
    }

    public String name() {
        return name;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    public String refusal() {
        return refusal;
    }

    @Deprecated
    public FunctionCall functionCall() {
        return functionCall;
    }

    @JsonAnyGetter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> customParameters() {
        return customParameters;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof AssistantMessage
                && equalTo((AssistantMessage) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(AssistantMessage another) {
        return Objects.equals(role, another.role)
                && Objects.equals(content, another.content)
                && Objects.equals(reasoningContent, another.reasoningContent)
                && Objects.equals(name, another.name)
                && Objects.equals(toolCalls, another.toolCalls)
                && Objects.equals(refusal, another.refusal)
                && Objects.equals(functionCall, another.functionCall)
                && Objects.equals(customParameters, another.customParameters);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(role);
        h += (h << 5) + Objects.hashCode(content);
        h += (h << 5) + Objects.hashCode(reasoningContent);
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(toolCalls);
        h += (h << 5) + Objects.hashCode(refusal);
        h += (h << 5) + Objects.hashCode(functionCall);
        h += (h << 5) + Objects.hashCode(customParameters);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "AssistantMessage{"
                + "role=" + role
                + ", content=" + content
                + ", reasoningContent=" + reasoningContent
                + ", name=" + name
                + ", toolCalls=" + toolCalls
                + ", refusal=" + refusal
                + ", functionCall=" + functionCall
                + ", customParameters=" + customParameters
                + "}";
    }

    public static AssistantMessage from(String content) {
        return AssistantMessage.builder()
                .content(content)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String content;
        private String reasoningContent;
        private String name;
        private List<ToolCall> toolCalls;
        private String refusal;
        @Deprecated
        private FunctionCall functionCall;
        private Map<String, Object> customParameters;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @JsonSetter
        public Builder toolCalls(List<ToolCall> toolCalls) {
            if (toolCalls != null) {
                this.toolCalls = unmodifiableList(toolCalls);
            }
            return this;
        }

        @JsonIgnore
        public Builder toolCalls(ToolCall... toolCalls) {
            return toolCalls(asList(toolCalls));
        }

        public Builder refusal(String refusal) {
            this.refusal = refusal;
            return this;
        }

        @Deprecated
        public Builder functionCall(FunctionCall functionCall) {
            this.functionCall = functionCall;
            return this;
        }

        public Builder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        public Builder customParameter(String key, Object value) {
            if (this.customParameters == null) {
                this.customParameters = new LinkedHashMap<>();
            }
            this.customParameters.put(key, value);
            return this;
        }

        public AssistantMessage build() {
            return new AssistantMessage(this);
        }
    }
}
