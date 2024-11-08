package dev.langchain4j.agent.tool;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents the parameters of a tool.
 *
 * @deprecated please use the new {@link JsonObjectSchema} API instead to define the schema for tool parameters.
 * Example:
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
public class ToolParameters {

    private final String type;
    private final Map<String, Map<String, Object>> properties;
    private final List<String> required;

    /**
     * Creates a {@link ToolParameters} from a {@link Builder}.
     *
     * @param builder the builder.
     */
    private ToolParameters(Builder builder) {
        this.type = builder.type;
        this.properties = builder.properties;
        this.required = builder.required;
    }

    /**
     * Returns the type of the tool.
     *
     * @return the type of the tool.
     */
    public String type() {
        return type;
    }

    /**
     * Returns the properties of the tool.
     *
     * @return the properties of the tool.
     */
    public Map<String, Map<String, Object>> properties() {
        return properties;
    }

    /**
     * Returns the required properties of the tool.
     *
     * @return the required properties of the tool.
     */
    public List<String> required() {
        return required;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolParameters tp
                && equalTo(tp);
    }

    /**
     * Utility method to compare two {@link ToolParameters}.
     *
     * @param another the other {@link ToolParameters}.
     * @return true if equal, false otherwise.
     */
    private boolean equalTo(ToolParameters another) {
        return Objects.equals(type, another.type)
                && Objects.equals(properties, another.properties)
                && Objects.equals(required, another.required);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(type);
        h += (h << 5) + Objects.hashCode(properties);
        h += (h << 5) + Objects.hashCode(required);
        return h;
    }

    @Override
    public String toString() {
        return "ToolParameters {"
                + " type = " + quoted(type)
                + ", properties = " + properties
                + ", required = " + required
                + " }";
    }

    /**
     * ToolParameters builder static inner class.
     *
     * @return a {@link Builder}.
     * @deprecated please use {@link JsonObjectSchema#builder()} instead
     */
    @Deprecated(forRemoval = true)
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code ToolParameters} builder static inner class.
     */
    public static final class Builder {
        private String type = "object";
        private Map<String, Map<String, Object>> properties = new HashMap<>();
        private List<String> required = new ArrayList<>();

        /**
         * Creates a {@link Builder}.
         */
        private Builder() {
        }

        /**
         * Sets the {@code type}.
         *
         * @param type the {@code type}
         * @return the {@code Builder}.
         * @deprecated please use {@link JsonObjectSchema#builder()} instead
         */
        @Deprecated(forRemoval = true)
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the {@code properties}.
         *
         * @param properties the {@code properties}
         * @return the {@code Builder}.
         * @deprecated please use {@link JsonObjectSchema.Builder#properties(Map)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder properties(Map<String, Map<String, Object>> properties) {
            this.properties = properties;
            return this;
        }

        /**
         * Sets the {@code required}.
         *
         * @param required the {@code required}
         * @return the {@code Builder}.
         * @deprecated please use {@link JsonObjectSchema.Builder#required(List)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder required(List<String> required) {
            this.required = required;
            return this;
        }

        /**
         * Returns a {@code ToolParameters} built from the parameters previously set.
         *
         * @return a {@code ToolParameters} built with parameters of this {@code ToolParameters.Builder}
         * @deprecated please use {@link JsonObjectSchema.Builder#build()} instead
         */
        @Deprecated(forRemoval = true)
        public ToolParameters build() {
            return new ToolParameters(this);
        }
    }
}
