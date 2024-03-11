package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.Experimental;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

@Experimental
@ToString
@EqualsAndHashCode
public class TableDefinition {

    private final String name;
    private final String description;
    private final Collection<ColumnDefinition> columns;

    public TableDefinition(String name, String description, Collection<ColumnDefinition> columns) {
        this.name = ensureNotBlank(name, "name");
        this.description = description;
        this.columns = ensureNotEmpty(columns, "columns");
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Collection<ColumnDefinition> columns() {
        return columns;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private Collection<ColumnDefinition> columns;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder columns(Collection<ColumnDefinition> columns) {
            this.columns = columns;
            return this;
        }

        public Builder addColumn(String name, String type) {
            if (columns == null) {
                columns = new ArrayList<>();
            }
            this.columns.add(new ColumnDefinition(name, type));
            return this;
        }

        public Builder addColumn(String name, String type, String description) {
            if (columns == null) {
                columns = new ArrayList<>();
            }
            this.columns.add(new ColumnDefinition(name, type, description));
            return this;
        }

        public TableDefinition build() {
            return new TableDefinition(name, description, columns);
        }
    }
}