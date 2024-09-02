package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AssistantMessage implements Message {

    private final Role role;
    private String content;
    private String name;
    private List<ToolCall> toolCalls;

    public AssistantMessage() {
        this.role = Role.ASSISTANT;
    }

    public AssistantMessage(String content, String name, List<ToolCall> toolCalls) {
        this.role = Role.ASSISTANT;
        this.content = content;
        this.name = name;
        this.toolCalls = toolCalls;
    }

    private AssistantMessage(Builder builder) {
        this.role = Role.ASSISTANT;
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

    public void setContent(String content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public static final class Builder {

        private String content;
        private String name;
        private List<ToolCall> toolCalls;

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