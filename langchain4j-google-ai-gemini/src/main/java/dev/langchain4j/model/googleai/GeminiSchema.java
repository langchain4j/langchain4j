package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiSchema {
    private GeminiType type;
    private String format;
    private String description;
    private Boolean nullable;

    @JsonProperty("enum")
    private List<String> enumeration;

    private String minItems;
    private String maxItems;
    private String minLength;
    private String maxLength;
    private String pattern;
    private Double minimum;
    private Double maximum;
    private Map<String, GeminiSchema> properties;
    private List<String> required;
    private GeminiSchema items;
    private List<GeminiSchema> anyOf;

    GeminiSchema(
            GeminiType type,
            String format,
            String description,
            Boolean nullable,
            List<String> enumeration,
            String minItems,
            String maxItems,
            String minLength,
            String maxLength,
            String pattern,
            Double minimum,
            Double maximum,
            Map<String, GeminiSchema> properties,
            List<String> required,
            GeminiSchema items,
            List<GeminiSchema> anyOf) {
        this.type = type;
        this.format = format;
        this.description = description;
        this.nullable = nullable;
        this.enumeration = enumeration;
        this.minItems = minItems;
        this.maxItems = maxItems;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.pattern = pattern;
        this.minimum = minimum;
        this.maximum = maximum;
        this.properties = properties;
        this.required = required;
        this.items = items;
        this.anyOf = anyOf;
    }

    public static GeminiSchemaBuilder builder() {
        return new GeminiSchemaBuilder();
    }

    public GeminiType getType() {
        return this.type;
    }

    public String getFormat() {
        return this.format;
    }

    public String getDescription() {
        return this.description;
    }

    public Boolean getNullable() {
        return this.nullable;
    }

    @JsonIgnore
    public List<String> getEnumeration() {
        return this.enumeration;
    }

    public String getMinItems() {
        return this.minItems;
    }

    public String getMaxItems() {
        return this.maxItems;
    }

    public String getMinLength() {
        return this.minLength;
    }

    public String getMaxLength() {
        return this.maxLength;
    }

    public String getPattern() {
        return this.pattern;
    }

    public Double getMinimum() {
        return this.minimum;
    }

    public Double getMaximum() {
        return this.maximum;
    }

    public Map<String, GeminiSchema> getProperties() {
        return this.properties;
    }

    public List<String> getRequired() {
        return this.required;
    }

    public GeminiSchema getItems() {
        return this.items;
    }

    public List<GeminiSchema> getAnyOf() {
        return this.anyOf;
    }

    public void setType(GeminiType type) {
        this.type = type;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public void setEnumeration(List<String> enumeration) {
        this.enumeration = enumeration;
    }

    public void setMinItems(String minItems) {
        this.minItems = minItems;
    }

    public void setMaxItems(String maxItems) {
        this.maxItems = maxItems;
    }

    public void setMinLength(String minLength) {
        this.minLength = minLength;
    }

    public void setMaxLength(String maxLength) {
        this.maxLength = maxLength;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setMinimum(Double minimum) {
        this.minimum = minimum;
    }

    public void setMaximum(Double maximum) {
        this.maximum = maximum;
    }

    public void setProperties(Map<String, GeminiSchema> properties) {
        this.properties = properties;
    }

    public void setRequired(List<String> required) {
        this.required = required;
    }

    public void setItems(GeminiSchema items) {
        this.items = items;
    }

    public void setAnyOf(List<GeminiSchema> anyOf) {
        this.anyOf = anyOf;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiSchema)) return false;
        final GeminiSchema other = (GeminiSchema) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
        final Object this$format = this.getFormat();
        final Object other$format = other.getFormat();
        if (this$format == null ? other$format != null : !this$format.equals(other$format)) return false;
        final Object this$description = this.getDescription();
        final Object other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description))
            return false;
        final Object this$nullable = this.getNullable();
        final Object other$nullable = other.getNullable();
        if (this$nullable == null ? other$nullable != null : !this$nullable.equals(other$nullable)) return false;
        final Object this$enumeration = this.getEnumeration();
        final Object other$enumeration = other.getEnumeration();
        if (this$enumeration == null ? other$enumeration != null : !this$enumeration.equals(other$enumeration))
            return false;
        final Object this$minItems = this.getMinItems();
        final Object other$minItems = other.getMinItems();
        if (this$minItems == null ? other$minItems != null : !this$minItems.equals(other$minItems)) return false;
        final Object this$maxItems = this.getMaxItems();
        final Object other$maxItems = other.getMaxItems();
        if (this$maxItems == null ? other$maxItems != null : !this$maxItems.equals(other$maxItems)) return false;
        final Object this$minLength = this.getMinLength();
        final Object other$minLength = other.getMinLength();
        if (this$minLength == null ? other$minLength != null : !this$minLength.equals(other$minLength)) return false;
        final Object this$maxLength = this.getMaxLength();
        final Object other$maxLength = other.getMaxLength();
        if (this$maxLength == null ? other$maxLength != null : !this$maxLength.equals(other$maxLength)) return false;
        final Object this$pattern = this.getPattern();
        final Object other$pattern = other.getPattern();
        if (this$pattern == null ? other$pattern != null : !this$pattern.equals(other$pattern)) return false;
        final Object this$minimum = this.getMinimum();
        final Object other$minimum = other.getMinimum();
        if (this$minimum == null ? other$minimum != null : !this$minimum.equals(other$minimum)) return false;
        final Object this$maximum = this.getMaximum();
        final Object other$maximum = other.getMaximum();
        if (this$maximum == null ? other$maximum != null : !this$maximum.equals(other$maximum)) return false;
        final Object this$properties = this.getProperties();
        final Object other$properties = other.getProperties();
        if (this$properties == null ? other$properties != null : !this$properties.equals(other$properties))
            return false;
        final Object this$required = this.getRequired();
        final Object other$required = other.getRequired();
        if (this$required == null ? other$required != null : !this$required.equals(other$required)) return false;
        final Object this$items = this.getItems();
        final Object other$items = other.getItems();
        if (this$items == null ? other$items != null : !this$items.equals(other$items)) return false;
        final Object this$anyOf = this.getAnyOf();
        final Object other$anyOf = other.getAnyOf();
        if (this$anyOf == null ? other$anyOf != null : !this$anyOf.equals(other$anyOf)) return false;

        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiSchema;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $format = this.getFormat();
        result = result * PRIME + ($format == null ? 43 : $format.hashCode());
        final Object $description = this.getDescription();
        result = result * PRIME + ($description == null ? 43 : $description.hashCode());
        final Object $nullable = this.getNullable();
        result = result * PRIME + ($nullable == null ? 43 : $nullable.hashCode());
        final Object $enumeration = this.getEnumeration();
        result = result * PRIME + ($enumeration == null ? 43 : $enumeration.hashCode());
        final Object $minItems = this.getMinItems();
        result = result * PRIME + ($minItems == null ? 43 : $minItems.hashCode());
        final Object $maxItems = this.getMaxItems();
        result = result * PRIME + ($maxItems == null ? 43 : $maxItems.hashCode());
        final Object $minLength = this.getMinLength();
        result = result * PRIME + ($minLength == null ? 43 : $minLength.hashCode());
        final Object $maxLength = this.getMaxLength();
        result = result * PRIME + ($maxLength == null ? 43 : $maxLength.hashCode());
        final Object $pattern = this.getPattern();
        result = result * PRIME + ($pattern == null ? 43 : $pattern.hashCode());
        final Object $minimum = this.getMinimum();
        result = result * PRIME + ($minimum == null ? 43 : $minimum.hashCode());
        final Object $maximum = this.getMaximum();
        result = result * PRIME + ($maximum == null ? 43 : $maximum.hashCode());
        final Object $properties = this.getProperties();
        result = result * PRIME + ($properties == null ? 43 : $properties.hashCode());
        final Object $required = this.getRequired();
        result = result * PRIME + ($required == null ? 43 : $required.hashCode());
        final Object $items = this.getItems();
        result = result * PRIME + ($items == null ? 43 : $items.hashCode());
        final Object $anyOf = this.getAnyOf();
        result = result * PRIME + ($anyOf == null ? 43 : $anyOf.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiSchema(type=" + this.getType() + ", format=" + this.getFormat() + ", description="
                + this.getDescription() + ", nullable=" + this.getNullable() + ", enumeration=" + this.getEnumeration()
                + ", minItems=" + this.getMinItems() + ", maxItems=" + this.getMaxItems() + ", minLength="
                + this.getMinLength() + ", maxLength=" + this.getMaxLength() + ", pattern=" + this.getPattern()
                + ", minimum=" + this.getMinimum() + ", maximum=" + this.getMaximum() + ", properties="
                + this.getProperties() + ", required=" + this.getRequired() + ", items=" + this.getItems() + ", anyOf="
                + this.getAnyOf() + ")";
    }

    public static class GeminiSchemaBuilder {
        private GeminiType type;
        private String format;
        private String description;
        private Boolean nullable;
        private List<String> enumeration;
        private String minItems;
        private String maxItems;
        private String minLength;
        private String maxLength;
        private String pattern;
        private Double minimum;
        private Double maximum;
        private Map<String, GeminiSchema> properties;
        private List<String> required;
        private GeminiSchema items;
        private List<GeminiSchema> anyOf;

        GeminiSchemaBuilder() {}

        public GeminiSchemaBuilder type(GeminiType type) {
            this.type = type;
            return this;
        }

        public GeminiSchemaBuilder format(String format) {
            this.format = format;
            return this;
        }

        public GeminiSchemaBuilder description(String description) {
            this.description = description;
            return this;
        }

        public GeminiSchemaBuilder nullable(Boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public GeminiSchemaBuilder enumeration(List<String> enumeration) {
            this.enumeration = enumeration;
            return this;
        }

        public GeminiSchemaBuilder minItems(String minItems) {
            this.minItems = minItems;
            return this;
        }

        public GeminiSchemaBuilder maxItems(String maxItems) {
            this.maxItems = maxItems;
            return this;
        }

        public GeminiSchemaBuilder minLength(String minLength) {
            this.minLength = minLength;
            return this;
        }

        public GeminiSchemaBuilder maxLength(String maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public GeminiSchemaBuilder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public GeminiSchemaBuilder minimum(Double minimum) {
            this.minimum = minimum;
            return this;
        }

        public GeminiSchemaBuilder maximum(Double maximum) {
            this.maximum = maximum;
            return this;
        }

        public GeminiSchemaBuilder properties(Map<String, GeminiSchema> properties) {
            this.properties = properties;
            return this;
        }

        public GeminiSchemaBuilder required(List<String> required) {
            this.required = required;
            return this;
        }

        public GeminiSchemaBuilder items(GeminiSchema items) {
            this.items = items;
            return this;
        }

        public GeminiSchemaBuilder anyOf(List<GeminiSchema> anyOf) {
            this.anyOf = anyOf;
            return this;
        }

        public GeminiSchema build() {
            return new GeminiSchema(
                    this.type,
                    this.format,
                    this.description,
                    this.nullable,
                    this.enumeration,
                    this.minItems,
                    this.maxItems,
                    this.minLength,
                    this.maxLength,
                    this.pattern,
                    this.minimum,
                    this.maximum,
                    this.properties,
                    this.required,
                    this.items,
                    this.anyOf);
        }

        public String toString() {
            return "GeminiSchema.GeminiSchemaBuilder(type=" + this.type + ", format=" + this.format + ", description="
                    + this.description + ", nullable=" + this.nullable + ", enumeration=" + this.enumeration
                    + ", minItems=" + this.minItems + ", maxItems=" + this.maxItems + ", minLength=" + this.minLength
                    + ", maxLength=" + this.maxLength + ", pattern=" + this.pattern + ", minimum=" + this.minimum
                    + ", maximum=" + this.maximum + ", properties=" + this.properties + ", required=" + this.required
                    + ", items=" + this.items + ", anyOf=" + this.anyOf + ")";
        }
    }
}
