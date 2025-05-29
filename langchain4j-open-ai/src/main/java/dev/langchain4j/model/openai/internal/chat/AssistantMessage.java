package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
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
    private final String name;
    @JsonProperty
    private final List<ToolCall> toolCalls;
    @JsonProperty
    private final Boolean refusal;
    @JsonProperty
    @Deprecated
    private final FunctionCall functionCall;

    public AssistantMessage(Builder builder) {
        this.content = builder.content;
        this.name = builder.name;
        this.toolCalls = builder.toolCalls;
        this.refusal = builder.refusal;
        this.functionCall = builder.functionCall;
    }

    public Role role() {
        return role;
    }

    public String content() {
        return content;
    }

    public String name() {
        return name;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    public Boolean refusal() {
        return refusal;
    }

    @Deprecated
    public FunctionCall functionCall() {
        return functionCall;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof AssistantMessage
                && equalTo((AssistantMessage) another);
    }

    private boolean equalTo(AssistantMessage another) {
        return Objects.equals(role, another.role)
                && Objects.equals(content, another.content)
                && Objects.equals(name, another.name)
                && Objects.equals(toolCalls, another.toolCalls)
                && Objects.equals(refusal, another.refusal)
                && Objects.equals(functionCall, another.functionCall);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(role);
        h += (h << 5) + Objects.hashCode(content);
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(toolCalls);
        h += (h << 5) + Objects.hashCode(refusal);
        h += (h << 5) + Objects.hashCode(functionCall);
        return h;
    }

    @Override
    public String toString() {
        return "AssistantMessage{"
                + "role=" + role
                + ", content=" + content
                + ", name=" + name
                + ", toolCalls=" + toolCalls
                + ", refusal=" + refusal
                + ", functionCall=" + functionCall
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
        private String name;
        private List<ToolCall> toolCalls;
        private Boolean refusal;
        @Deprecated
        private FunctionCall functionCall;

        public Builder content(String content) {
            this.content = content;
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

        public Builder refusal(Boolean refusal) {
            this.refusal = refusal;
            return this;
        }

        @Deprecated
        public Builder functionCall(FunctionCall functionCall) {
            this.functionCall = functionCall;
            return this;
        }

        public AssistantMessage build() {
            return new AssistantMessage(this);
        }
    }
}
