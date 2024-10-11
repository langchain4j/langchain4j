package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
class Message {

    private Role role;
    private String content;
    private List<String> images;
    private List<ToolCall> toolCalls;

    Message() {
    }

    public Message(Role role, String content, List<String> images, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.images = images;
        this.toolCalls = toolCalls;
    }

    static Builder builder() {
        return new Builder();
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    static class Builder {

        private Role role;
        private String content;
        private List<String> images;
        private List<ToolCall> toolCalls;

        Builder role(Role role) {
            this.role = role;
            return this;
        }

        Builder content(String content) {
            this.content = content;
            return this;
        }

        Builder images(List<String> images) {
            this.images = images;
            return this;
        }

        Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        Message build() {
            return new Message(role, content, images, toolCalls);
        }
    }
}
