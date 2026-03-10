package dev.langchain4j.model.anthropic;

import dev.langchain4j.Experimental;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;

/**
 * @since 1.10.0
 */
@Experimental
public class AnthropicServerTool {

    private final String type;
    private final String name;
    private final Map<String, Object> attributes;

    public AnthropicServerTool(Builder builder) {
        this.type = builder.type;
        this.name = builder.name;
        this.attributes = copy(builder.attributes);
    }

    public String type() {
        return type;
    }

    public String name() {
        return name;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicServerTool that = (AnthropicServerTool) o;
        return Objects.equals(type, that.type)
                && Objects.equals(name, that.name)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, attributes);
    }

    @Override
    public String toString() {
        return "AnthropicServerTool{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", attributes=" + attributes +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String type;
        private String name;
        private Map<String, Object> attributes;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder addAttribute(String key, Object value) {
            if (this.attributes == null) {
                this.attributes = new LinkedHashMap<>();
            }
            this.attributes.put(key, value);
            return this;
        }

        public AnthropicServerTool build() {
            return new AnthropicServerTool(this);
        }
    }
}
