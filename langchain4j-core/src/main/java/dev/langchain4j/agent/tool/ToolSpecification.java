package dev.langchain4j.agent.tool;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.mutableCopy;
import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.ToolSpecificationJsonUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Describes a tool that language model can execute.
 * <p>
 * Can be generated automatically from methods annotated with {@link Tool} using {@link ToolSpecifications} helper.
 */
public class ToolSpecification {

    public static final String METADATA_SEARCH_BEHAVIOR = "searchBehavior";

    private final String name;
    private final String description;
    private final JsonObjectSchema parameters;
    private final Map<String, Object> metadata;
    private final Boolean strict;
    private final ReturnBehavior returnBehavior;

    /**
     * Creates a {@link ToolSpecification} from a {@link Builder}.
     *
     * @param builder the builder.
     */
    private ToolSpecification(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters;
        this.metadata = copy(builder.metadata);
        this.strict = builder.strict;
        this.returnBehavior = builder.returnBehavior;
    }

    /**
     * Returns the name of the tool.
     *
     * @return the name of the tool.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the description of the tool.
     *
     * @return the description of the tool.
     */
    public String description() {
        return description;
    }

    /**
     * Returns the parameters of the tool.
     */
    public JsonObjectSchema parameters() {
        return parameters;
    }

    /**
     * Returns the metadata relevant to the tool.
     * <p>
     * NOTE: this metadata is not sent to the LLM provider API by default,
     * you must explicitly specify which metadata keys should be sent when creating a {@link ChatModel}.
     * <p>
     * NOTE: Currently, tool metadata is supported only by the {@code langchain4j-anthropic} module.
     */
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Returns whether this tool should use strict schema enforcement.
     * <p>
     * When {@code true}, the LLM provider will validate tool calls against this tool's schema server-side.
     * When {@code false}, strict enforcement is explicitly disabled for this tool.
     * When {@code null} (default), the model-level strict setting is used.
     * <p>
     * NOTE: Currently, per-tool strict is supported by the {@code langchain4j-anthropic}
     * and {@code langchain4j-open-ai} modules.
     *
     * @return {@code true} to enable strict enforcement, {@code false} to disable, or {@code null} to use the model default.
     */
    public Boolean strict() {
        return strict;
    }

    /**
     * Returns the {@link ReturnBehavior} for this tool, or {@code null} if not set.
     * <p>
     * When {@code null}, the AI Service default ({@link ReturnBehavior#TO_LLM}) is used.
     * This field is not sent to the LLM provider — it controls client-side behavior only.
     *
     * @since 1.16.0
     */
    @Experimental
    public ReturnBehavior returnBehavior() {
        return returnBehavior;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolSpecification ts && equalTo(ts);
    }

    private boolean equalTo(ToolSpecification another) {
        return Objects.equals(name, another.name)
                && Objects.equals(description, another.description)
                && Objects.equals(parameters, another.parameters)
                && Objects.equals(metadata, another.metadata)
                && Objects.equals(strict, another.strict)
                && returnBehavior == another.returnBehavior;
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(description);
        h += (h << 5) + Objects.hashCode(parameters);
        h += (h << 5) + Objects.hashCode(metadata);
        h += (h << 5) + Objects.hashCode(strict);
        h += (h << 5) + Objects.hashCode(returnBehavior);
        return h;
    }

    @Override
    public String toString() {
        return "ToolSpecification {"
                + " name = " + quoted(name)
                + ", description = " + quoted(description)
                + ", parameters = " + parameters
                + ", metadata = " + metadata
                + ", strict = " + strict
                + ", returnBehavior = " + returnBehavior
                + " }";
    }

    /**
     * Serializes this {@link ToolSpecification} to a JSON string.
     *
     * @return a JSON string representing this tool specification.
     * @see #fromJson(String)
     */
    public String toJson() {
        return ToolSpecificationJsonUtils.toJson(this);
    }

    /**
     * Deserializes a {@link ToolSpecification} from a JSON string.
     *
     * @param json the JSON string to deserialize.
     * @return the deserialized {@link ToolSpecification}.
     * @see #toJson()
     */
    public static ToolSpecification fromJson(String json) {
        return ToolSpecificationJsonUtils.fromJson(json);
    }

    public Builder toBuilder() {
        return builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .metadata(mutableCopy(metadata))
                .strict(strict)
                .returnBehavior(returnBehavior);
    }

    /**
     * Creates builder to build {@link ToolSpecification}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code ToolSpecification} builder static inner class.
     */
    public static final class Builder {

        private String name;
        private String description;
        private JsonObjectSchema parameters;
        private Map<String, Object> metadata;
        private Boolean strict;
        private ReturnBehavior returnBehavior;

        /**
         * Creates a {@link Builder}.
         */
        private Builder() {
            this.metadata = new HashMap<>();
        }

        /**
         * Sets the {@code name}.
         *
         * @param name the {@code name}
         * @return {@code this}
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the {@code description}.
         *
         * @param description the {@code description}
         * @return {@code this}
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the {@code parameters}.
         *
         * @param parameters the {@code parameters}
         * @return {@code this}
         */
        public Builder parameters(JsonObjectSchema parameters) {
            this.parameters = parameters;
            return this;
        }

        /**
         * Sets the {@code metadata}.
         *
         * @param metadata
         * @return {@code this}
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Adds a key-value pair to the tool's {@code metadata}.
         */
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Sets whether this tool should use strict schema enforcement.
         * <p>
         * When {@code true}, the LLM provider will validate tool calls against this tool's schema server-side.
         * When {@code false}, strict enforcement is explicitly disabled for this tool.
         * When {@code null} (default), the model-level strict setting is used.
         *
         * @param strict whether to enable strict enforcement for this tool
         * @return {@code this}
         */
        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        /**
         * Sets the {@link ReturnBehavior} for this tool.
         * <p>
         * When set, this value is used by the AI Service to determine whether to return
         * tool results immediately to the caller or send them back to the LLM for further processing.
         * This field is not sent to the LLM provider — it controls client-side behavior only.
         * <p>
         * When {@code null} (default), the AI Service default ({@link ReturnBehavior#TO_LLM}) is used.
         *
         * @param returnBehavior the return behavior for this tool
         * @return {@code this}
         * @since 1.16.0
         */
        @Experimental
        public Builder returnBehavior(ReturnBehavior returnBehavior) {
            this.returnBehavior = returnBehavior;
            return this;
        }

        /**
         * Returns a {@code ToolSpecification} built from the parameters previously set.
         *
         * @return a {@code ToolSpecification} built with parameters of this {@code ToolSpecification.Builder}
         */
        public ToolSpecification build() {
            return new ToolSpecification(this);
        }
    }
}
