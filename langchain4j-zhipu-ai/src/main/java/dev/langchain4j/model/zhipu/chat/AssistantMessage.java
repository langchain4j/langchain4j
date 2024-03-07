package dev.langchain4j.model.zhipu.chat;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

import static dev.langchain4j.model.zhipu.chat.Role.ASSISTANT;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

@ToString
@EqualsAndHashCode
public final class AssistantMessage implements Message {

    private final Role role = ASSISTANT;
    @Getter
    private final String content;
    @Getter
    private final String name;
    @Getter
    @SerializedName("tool_calls")
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