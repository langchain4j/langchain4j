package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicMessage {

    public AnthropicRole role;
    public List<AnthropicMessageContent> content;

    public AnthropicMessage() {}

    public AnthropicMessage(AnthropicRole role, List<AnthropicMessageContent> content) {
        this.role = role;
        this.content = content;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicMessage that = (AnthropicMessage) o;
        return role == that.role && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }

    @Override
    public String toString() {
        return "AnthropicMessage{" + "role=" + role + ", content=" + content + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private AnthropicRole role;
        private List<AnthropicMessageContent> content;

        public Builder role(AnthropicRole role) {
            this.role = role;
            return this;
        }

        public Builder content(List<AnthropicMessageContent> content) {
            this.content = content;
            return this;
        }

        public AnthropicMessage build() {
            return new AnthropicMessage(role, content);
        }
    }
}
