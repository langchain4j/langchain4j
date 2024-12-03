package dev.langchain4j.agent.tool;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;

/**
 * Describes a tool that language model can execute.
 * <p>
 * Can be generated automatically from methods annotated with {@link Tool} using {@link ToolSpecifications} helper.
 */
public class ToolSpecification {

    private final String name;
    private final String description;
    private final JsonObjectSchema parameters;
    @Deprecated(forRemoval = true)
    private final ToolParameters toolParameters;

    /**
     * Creates a {@link ToolSpecification} from a {@link Builder}.
     *
     * @param builder the builder.
     */
    private ToolSpecification(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        if (builder.parameters != null && builder.toolParameters != null) {
            throw new IllegalArgumentException("Both (new) JsonObjectSchema and (old) ToolParameters " +
                    "are used to specify tool parameters. Please use only (new) JsonObjectSchema.");
        }
        this.parameters = builder.parameters;
        this.toolParameters = builder.toolParameters;
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
     * <p>
     * The old method that returns the deprecated {@link ToolParameters} has been renamed to {@link #toolParameters()}.
     */
    public JsonObjectSchema parameters() {
        return parameters;
    }

    /**
     * @deprecated please use {@link #parameters()} instead
     */
    @Deprecated(forRemoval = true)
    public ToolParameters toolParameters() {
        return toolParameters;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolSpecification ts
                && equalTo(ts);
    }

    private boolean equalTo(ToolSpecification another) {
        return Objects.equals(name, another.name)
                && Objects.equals(description, another.description)
                && Objects.equals(parameters, another.parameters)
                && Objects.equals(toolParameters, another.toolParameters);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(description);
        h += (h << 5) + Objects.hashCode(parameters);
        h += (h << 5) + Objects.hashCode(toolParameters);
        return h;
    }

    @Override
    public String toString() {
        return "ToolSpecification {"
                + " name = " + quoted(name)
                + ", description = " + quoted(description)
                + ", parameters = " + parameters
                + ", toolParameters = " + toolParameters
                + " }";
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
        @Deprecated(forRemoval = true)
        private ToolParameters toolParameters;

        /**
         * Creates a {@link Builder}.
         */
        private Builder() {
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
         * Sets the {@code parameters}.
         *
         * @param parameters the {@code parameters}
         * @return {@code this}
         * @deprecated please use {@link #parameters(JsonObjectSchema)} instead. Example:
         * <pre>
         * ToolSpecification.builder()
         *     .name("weather")
         *     .description("Returns the current weather in the specified city")
         *     .parameters(JsonObjectSchema.builder()
         *         .addStringProperty("city", "The name of the city, e.g., Munich")
         *         .addEnumProperty("units", List.of("CELSIUS", "FAHRENHEIT"))
         *         .required("city") // please specify mandatory properties explicitly
         *         .build())
         *     .build();
         * </pre>
         */
        @Deprecated(forRemoval = true)
        public Builder parameters(ToolParameters parameters) {
            this.toolParameters = parameters;
            return this;
        }

        /**
         * Adds a parameter to the tool.
         *
         * @param name                 the name of the parameter.
         * @param jsonSchemaProperties the properties of the parameter.
         * @return {@code this}
         * @deprecated please use {@link Builder#parameters(JsonObjectSchema)} instead. Example:
         * <pre>
         * ToolSpecification.builder()
         *     .name("weather")
         *     .description("Returns the current weather in the specified city")
         *     .parameters(JsonObjectSchema.builder()
         *         .addStringProperty("city", "The name of the city, e.g., Munich")
         *         .addEnumProperty("units", List.of("CELSIUS", "FAHRENHEIT"))
         *         .required("city") // please specify mandatory properties explicitly
         *         .build())
         *     .build();
         * </pre>
         */
        @Deprecated(forRemoval = true)
        public Builder addParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
            return addParameter(name, asList(jsonSchemaProperties));
        }

        /**
         * Adds a parameter to the tool.
         *
         * @param name                 the name of the parameter.
         * @param jsonSchemaProperties the properties of the parameter.
         * @return {@code this}
         * @deprecated please use {@link Builder#parameters(JsonObjectSchema)} instead. Example:
         * <pre>
         * ToolSpecification.builder()
         *     .name("weather")
         *     .description("Returns the current weather in the specified city")
         *     .parameters(JsonObjectSchema.builder()
         *         .addStringProperty("city", "The name of the city, e.g., Munich")
         *         .addEnumProperty("units", List.of("CELSIUS", "FAHRENHEIT"))
         *         .required("city") // please specify mandatory properties explicitly
         *         .build())
         *     .build();
         * </pre>
         */
        @Deprecated(forRemoval = true)
        public Builder addParameter(String name, Iterable<JsonSchemaProperty> jsonSchemaProperties) {
            addOptionalParameter(name, jsonSchemaProperties);
            this.toolParameters.required().add(name);
            return this;
        }

        /**
         * Adds an optional parameter to the tool.
         *
         * @param name                 the name of the parameter.
         * @param jsonSchemaProperties the properties of the parameter.
         * @return {@code this}
         * @deprecated please use {@link Builder#parameters(JsonObjectSchema)} instead. Example:
         * <pre>
         * ToolSpecification.builder()
         *     .name("weather")
         *     .description("Returns the current weather in the specified city")
         *     .parameters(JsonObjectSchema.builder()
         *         .addStringProperty("city", "The name of the city, e.g., Munich")
         *         .addEnumProperty("units", List.of("CELSIUS", "FAHRENHEIT"))
         *         .required("city") // please specify mandatory properties explicitly
         *         .build())
         *     .build();
         * </pre>
         */
        @Deprecated(forRemoval = true)
        public Builder addOptionalParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
            return addOptionalParameter(name, asList(jsonSchemaProperties));
        }

        /**
         * Adds an optional parameter to the tool.
         *
         * @param name                 the name of the parameter.
         * @param jsonSchemaProperties the properties of the parameter.
         * @return {@code this}
         * @deprecated please use {@link Builder#parameters(JsonObjectSchema)} instead. Example:
         * <pre>
         * ToolSpecification.builder()
         *     .name("weather")
         *     .description("Returns the current weather in the specified city")
         *     .parameters(JsonObjectSchema.builder()
         *         .addStringProperty("city", "The name of the city, e.g., Munich")
         *         .addEnumProperty("units", List.of("CELSIUS", "FAHRENHEIT"))
         *         .required("city") // please specify mandatory properties explicitly
         *         .build())
         *     .build();
         * </pre>
         */
        @Deprecated(forRemoval = true)
        public Builder addOptionalParameter(String name, Iterable<JsonSchemaProperty> jsonSchemaProperties) {
            if (this.toolParameters == null) {
                this.toolParameters = ToolParameters.builder().build();
            }

            Map<String, Object> jsonSchemaPropertiesMap = new HashMap<>();
            for (JsonSchemaProperty jsonSchemaProperty : jsonSchemaProperties) {
                jsonSchemaPropertiesMap.put(jsonSchemaProperty.key(), jsonSchemaProperty.value());
            }

            this.toolParameters.properties().put(name, jsonSchemaPropertiesMap);
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
