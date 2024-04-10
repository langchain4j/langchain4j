package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.Experimental;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

@Experimental
@ToString
@EqualsAndHashCode
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
}