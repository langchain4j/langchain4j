package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicTool {

    public String name;
    public String description;
    public AnthropicToolSchema inputSchema;
    public AnthropicCacheControl cacheControl;

    public AnthropicTool() {}

    public AnthropicTool(
            String name, String description, AnthropicToolSchema inputSchema, AnthropicCacheControl cacheControl) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.cacheControl = cacheControl;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicTool that = (AnthropicTool) o;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(inputSchema, that.inputSchema)
                && Objects.equals(cacheControl, that.cacheControl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, inputSchema, cacheControl);
    }

    @Override
    public String toString() {
        return "AnthropicTool{" + "name='"
                + name + '\'' + ", description='"
                + description + '\'' + ", inputSchema="
                + inputSchema + ", cacheControl="
                + cacheControl + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private AnthropicToolSchema inputSchema;
        private AnthropicCacheControl cacheControl;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(AnthropicToolSchema inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder cacheControl(AnthropicCacheControl cacheControl) {
            this.cacheControl = cacheControl;
            return this;
        }

        public AnthropicTool build() {
            return new AnthropicTool(name, description, inputSchema, cacheControl);
        }
    }
}
