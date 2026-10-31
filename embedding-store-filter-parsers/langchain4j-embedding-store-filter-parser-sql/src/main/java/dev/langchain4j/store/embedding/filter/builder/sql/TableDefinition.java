package dev.langchain4j.store.embedding.filter.builder.sql;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

@Experimental
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

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof TableDefinition)) return false;
        final TableDefinition other = (TableDefinition) o;
        if (!other.canEqual((Object) this)) return false;
        return Objects.equals(name, other.name)
                && Objects.equals(description, other.description)
                && Objects.equals(columns, other.columns);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof TableDefinition;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.name;
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $description = this.description;
        result = result * PRIME + ($description == null ? 43 : $description.hashCode());
        final Object $columns = this.columns;
        result = result * PRIME + ($columns == null ? 43 : $columns.hashCode());
        return result;
    }

    public String toString() {
        return "TableDefinition(name=" + this.name + ", description=" + this.description + ", columns=" + this.columns
                + ")";
    }

    public static class Builder {

        private String name;
        private String description;
        private Collection<ColumnDefinition> columns;

        /**
         * Sets the table name used in the generated {@code CREATE TABLE} statement.
         *
         * @param name the table name
         * @return {@code this}
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets an optional description of the table, appended as a {@code COMMENT} in the
         * generated {@code CREATE TABLE} statement.
         *
         * @param description the table description
         * @return {@code this}
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets all column definitions at once, replacing any previously added columns.
         *
         * @param columns the column definitions
         * @return {@code this}
         */
        public Builder columns(Collection<ColumnDefinition> columns) {
            this.columns = columns;
            return this;
        }

        /**
         * Adds a column with a name and SQL type to the table definition.
         *
         * @param name the column name (corresponds to a metadata key)
         * @param type the SQL column type (e.g. {@code "VARCHAR"}, {@code "INTEGER"})
         * @return {@code this}
         */
        public Builder addColumn(String name, String type) {
            if (columns == null) {
                columns = new ArrayList<>();
            }
            this.columns.add(new ColumnDefinition(name, type));
            return this;
        }

        /**
         * Adds a column with a name, SQL type, and description comment to the table definition.
         * The description is rendered as an inline SQL comment (e.g. {@code -- one of [a, b, c]}).
         *
         * @param name        the column name (corresponds to a metadata key)
         * @param type        the SQL column type (e.g. {@code "VARCHAR"}, {@code "INTEGER"})
         * @param description a hint describing allowed values, rendered as an inline SQL comment
         * @return {@code this}
         */
        public Builder addColumn(String name, String type, String description) {
            if (columns == null) {
                columns = new ArrayList<>();
            }
            this.columns.add(new ColumnDefinition(name, type, description));
            return this;
        }

        /**
         * Builds the {@link TableDefinition}.
         *
         * @return the configured {@link TableDefinition}
         */
        public TableDefinition build() {
            return new TableDefinition(name, description, columns);
        }
    }
}
