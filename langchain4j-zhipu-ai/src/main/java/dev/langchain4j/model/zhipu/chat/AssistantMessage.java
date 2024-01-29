package dev.langchain4j.model.zhipu.chat;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.model.zhipu.chat.Role.ASSISTANT;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public final class AssistantMessage implements Message {

    private final Role role = ASSISTANT;
    private final String content;
    private final String name;
    private final List<ToolCall> toolCalls;

    private AssistantMessage(Builder builder) {
        this.content = builder.content;
        this.name = builder.name;
        this.toolCalls = builder.toolCalls;
    }

    public static AssistantMessage from(String content) {
        return AssistantMessage.builder()
                .content(content)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getName() {
        return name;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
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
                && Objects.equals(toolCalls, another.toolCalls);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(role);
        h += (h << 5) + Objects.hashCode(content);
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(toolCalls);
        return h;
    }

    @Override
    public String toString() {
        return "AssistantMessage{"
                + "role=" + role
                + ", content=" + content
                + ", name=" + name
                + ", toolCalls=" + toolCalls
                + "}";
    }

    public static final class Builder {

        private String content;
        private String name;
        private List<ToolCall> toolCalls;

        private Builder() {
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder toolCalls(ToolCall... toolCalls) {
            return toolCalls(asList(toolCalls));
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            if (toolCalls != null) {
                this.toolCalls = unmodifiableList(toolCalls);
            }
            return this;
        }

        public AssistantMessage build() {
            return new AssistantMessage(this);
        }
    }
}