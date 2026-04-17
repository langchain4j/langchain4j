package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI Responses API server-side tool entry.
 *
 * <p>This class intentionally mirrors Anthropic server tools and keeps the public API thin.
 * Provider-specific configuration is carried via {@link #attributes()} and interpreted by the
 * OpenAI mapper layer.
 */
@Experimental
public class OpenAiOfficialServerTool {

    private final String type;
    private final String name;
    private final Map<String, Object> attributes;

    public OpenAiOfficialServerTool(Builder builder) {
        this.type = ensureNotBlank(builder.type, "type");
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
        OpenAiOfficialServerTool that = (OpenAiOfficialServerTool) o;
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
        return "OpenAiOfficialServerTool{" + "type='"
                + type + '\'' + ", name='"
                + name + '\'' + ", attributes="
                + attributes + '}';
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

        public OpenAiOfficialServerTool build() {
            return new OpenAiOfficialServerTool(this);
        }
    }
}
