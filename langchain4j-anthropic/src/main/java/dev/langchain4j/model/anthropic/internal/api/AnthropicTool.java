package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicTool {

    public String name;
    public String description;
    public AnthropicToolSchema inputSchema;
    public AnthropicCacheControl cacheControl;
    @JsonIgnore
    public Map<String, Object> customParameters;

    public AnthropicTool() {}

    public AnthropicTool(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.inputSchema = builder.inputSchema;
        this.cacheControl = builder.cacheControl;
        this.customParameters = builder.customParameters;
    }

    /**
     * @deprecated please use {@link #AnthropicTool(Builder)} instead
     */
    @Deprecated(since = "1.10.0", forRemoval = true)
    public AnthropicTool(
            String name, String description, AnthropicToolSchema inputSchema, AnthropicCacheControl cacheControl) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.cacheControl = cacheControl;
    }

    @JsonAnyGetter
    public Map<String, Object> customParameters() {
        return customParameters;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicTool that = (AnthropicTool) o;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(inputSchema, that.inputSchema)
                && Objects.equals(cacheControl, that.cacheControl)
                && Objects.equals(customParameters, that.customParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, inputSchema, cacheControl, customParameters);
    }

    @Override
    public String toString() {
        return "AnthropicTool{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", inputSchema=" + inputSchema +
                ", cacheControl=" + cacheControl +
                ", customParameters=" + customParameters +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private AnthropicToolSchema inputSchema;
        private AnthropicCacheControl cacheControl;
        private Map<String, Object> customParameters;

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

        public Builder customParameters(Map<String, Object> customParameters) {
            this.customParameters = customParameters;
            return this;
        }

        public AnthropicTool build() {
            return new AnthropicTool(this);
        }
    }
}
