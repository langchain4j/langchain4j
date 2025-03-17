package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.internal.Utils;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.model.zhipu.chat.Role.TOOL;

@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ToolMessage implements Message {

    private Role role;
    private String toolCallId;
    private String content;

    public ToolMessage(Role role, String toolCallId, String content) {
        this.role = Utils.getOrDefault(role, TOOL);
        this.toolCallId = toolCallId;
        this.content = content;
    }

    public static ToolMessage from(String toolCallId, String content) {
        return ToolMessage.builder()
                .toolCallId(toolCallId)
                .content(content)
                .build();
    }

    public static ToolMessageBuilder builder() {
        return new ToolMessageBuilder();
    }

    @Override
    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public static class ToolMessageBuilder {
        private Role role;
        private String toolCallId;
        private String content;

        public ToolMessageBuilder role(Role role) {
            this.role = role;
            return this;
        }

        public ToolMessageBuilder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public ToolMessageBuilder content(String content) {
            this.content = content;
            return this;
        }

        public ToolMessage build() {
            return new ToolMessage(this.role, this.toolCallId, this.content);
        }
    }
}