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
    private String maxItems;
    private Map<String, GeminiSchema> properties;
    private List<String> required;
    private GeminiSchema items;

    GeminiSchema(GeminiType type, String format, String description, Boolean nullable, List<String> enumeration, String maxItems, Map<String, GeminiSchema> properties, List<String> required, GeminiSchema items) {
        this.type = type;
        this.format = format;
        this.description = description;
        this.nullable = nullable;
        this.enumeration = enumeration;
        this.maxItems = maxItems;
        this.properties = properties;
        this.required = required;
        this.items = items;
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

    public String getMaxItems() {
        return this.maxItems;
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

    public void setMaxItems(String maxItems) {
        this.maxItems = maxItems;
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
        final Object this$maxItems = this.getMaxItems();
        final Object other$maxItems = other.getMaxItems();
        if (this$maxItems == null ? other$maxItems != null : !this$maxItems.equals(other$maxItems)) return false;
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
        final Object $maxItems = this.getMaxItems();
        result = result * PRIME + ($maxItems == null ? 43 : $maxItems.hashCode());
        final Object $properties = this.getProperties();
        result = result * PRIME + ($properties == null ? 43 : $properties.hashCode());
        final Object $required = this.getRequired();
        result = result * PRIME + ($required == null ? 43 : $required.hashCode());
        final Object $items = this.getItems();
        result = result * PRIME + ($items == null ? 43 : $items.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiSchema(type=" + this.getType() + ", format=" + this.getFormat() + ", description=" + this.getDescription() + ", nullable=" + this.getNullable() + ", enumeration=" + this.getEnumeration() + ", maxItems=" + this.getMaxItems() + ", properties=" + this.getProperties() + ", required=" + this.getRequired() + ", items=" + this.getItems() + ")";
    }

    public static class GeminiSchemaBuilder {
        private GeminiType type;
        private String format;
        private String description;
        private Boolean nullable;
        private List<String> enumeration;
        private String maxItems;
        private Map<String, GeminiSchema> properties;
        private List<String> required;
        private GeminiSchema items;

        GeminiSchemaBuilder() {
        }

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

        public GeminiSchemaBuilder maxItems(String maxItems) {
            this.maxItems = maxItems;
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

        public GeminiSchema build() {
            return new GeminiSchema(this.type, this.format, this.description, this.nullable, this.enumeration, this.maxItems, this.properties, this.required, this.items);
        }

        public String toString() {
            return "GeminiSchema.GeminiSchemaBuilder(type=" + this.type + ", format=" + this.format + ", description=" + this.description + ", nullable=" + this.nullable + ", enumeration=" + this.enumeration + ", maxItems=" + this.maxItems + ", properties=" + this.properties + ", required=" + this.required + ", items=" + this.items + ")";
        }
    }
}
