package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicToolUseContent extends AnthropicMessageContent {

    public String id;
    public String name;
    public Map<String, Object> input;

    public AnthropicToolUseContent(String id, String name, Map<String, Object> input) {
        super("tool_use");
        this.id = id;
        this.name = name;
        this.input = input;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AnthropicToolUseContent that = (AnthropicToolUseContent) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name) && Objects.equals(input, that.input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, name, input);
    }

    @Override
    public String toString() {
        return "AnthropicToolUseContent{" + "input="
                + input + ", type='"
                + type + '\'' + ", cacheControl="
                + cacheControl + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private String name;
        private Map<String, Object> input;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        public AnthropicToolUseContent build() {
            return new AnthropicToolUseContent(id, name, input);
        }
    }
}
