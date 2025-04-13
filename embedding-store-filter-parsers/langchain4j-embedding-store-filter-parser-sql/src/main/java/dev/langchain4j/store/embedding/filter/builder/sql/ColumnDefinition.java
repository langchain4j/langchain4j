package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.Experimental;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

@Experimental
public class ColumnDefinition {

    private final String name;
    private final String type;
    private final String description;

    public ColumnDefinition(String name, String type) {
        this(name, type, null);
    }

    public ColumnDefinition(String name, String type, String description) {
        this.name = ensureNotBlank(name, "name");
        this.type = ensureNotBlank(type, "type");
        this.description = description;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String description() {
        return description;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ColumnDefinition)) return false;
        final ColumnDefinition other = (ColumnDefinition) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$name = this.name;
        final Object other$name = other.name;
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$type = this.type;
        final Object other$type = other.type;
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
        final Object this$description = this.description;
        final Object other$description = other.description;
        if (this$description == null ? other$description != null : !this$description.equals(other$description))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ColumnDefinition;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.name;
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $type = this.type;
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $description = this.description;
        result = result * PRIME + ($description == null ? 43 : $description.hashCode());
        return result;
    }

    public String toString() {
        return "ColumnDefinition(name=" + this.name + ", type=" + this.type + ", description=" + this.description + ")";
    }
}
