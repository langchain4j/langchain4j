package dev.langchain4j.store.embedding.filter.builder.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Defines the structure of metadata fields that can be used in filters.
 * 
 * <p>A TableDefinition describes the available metadata columns, their types,
 * and descriptions that help the language model understand how to create
 * appropriate filters. This is essential for converting natural language
 * queries into structured filter operations.
 * 
 * <p>Example usage:
 * <pre>{@code
 * TableDefinition tableDefinition = TableDefinition.builder()
 *     .addColumn("author", String.class, "The document author")
 *     .addColumn("publishDate", java.time.LocalDate.class, "Publication date")
 *     .addColumn("rating", Number.class, "User rating from 1-5")
 *     .addColumn("category", CategoryEnum.class, "Document category")
 *     .build();
 * }</pre>
 * 
 * <p>The class supports various data types including:
 * <ul>
 *   <li>Primitive types: String, Number, Boolean</li>
 *   <li>Date types: LocalDate, LocalDateTime</li>
 *   <li>Enum types for categorical data</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class TableDefinition {

    private final List<ColumnDefinition> columns;

    /**
     * Constructs a new TableDefinition with the specified columns.
     * 
     * @param columns the list of column definitions. Must not be null.
     * @throws NullPointerException if columns is null
     */
    public TableDefinition(List<ColumnDefinition> columns) {
        this.columns = Objects.requireNonNull(columns, "columns cannot be null");
    }

    /**
     * Returns the list of column definitions for this table.
     * 
     * @return an unmodifiable list of column definitions
     */
    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableDefinition that = (TableDefinition) o;
        return Objects.equals(columns, that.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns);
    }

    @Override
    public String toString() {
        return "TableDefinition{" +
                "columns=" + columns +
                '}';
    }

    /**
     * Creates a new builder for constructing TableDefinition instances.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<ColumnDefinition> columns = new ArrayList<>();

        public Builder columns(Collection<ColumnDefinition> columns) {
            ensureNotEmpty(columns, "columns");
            this.columns.clear();
            this.columns.addAll(columns);
            return this;
        }

        public Builder addColumn(String name, Class<?> type) {
            return addColumn(name, type, null);
        }

        public Builder addColumn(String name, Class<?> type, String description) {
            ensureNotBlank(name, "name");
            Objects.requireNonNull(type, "type cannot be null");
            this.columns.add(new ColumnDefinition(name, type, description));
            return this;
        }

        public Builder addColumn(String name, String type) {
            return addColumn(name, type, null);
        }

        public Builder addColumn(String name, String type, String description) {
            ensureNotBlank(name, "name");
            ensureNotBlank(type, "type");
            this.columns.add(ColumnDefinition.fromString(name, type, description));
            return this;
        }

        public Builder addColumn(ColumnDefinition column) {
            Objects.requireNonNull(column, "column cannot be null");
            this.columns.add(column);
            return this;
        }

        public TableDefinition build() {
            ensureNotEmpty(columns, "columns");
            return new TableDefinition(new ArrayList<>(columns));
        }
    }

    private static void ensureNotBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be null or blank");
        }
    }

    private static void ensureNotEmpty(Collection<?> collection, String name) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be null or empty");
        }
    }

    /**
     * Represents a single column in a table definition.
     * 
     * <p>A ColumnDefinition describes a metadata field that can be used
     * in filter operations, including its name, data type, and description.
     * The description helps the language model understand when and how
     * to use this column in filters.
     * 
     * <p>The class supports both Class-based type definitions and string-based
     * types for backward compatibility. Enum types are automatically detected
     * and their possible values are made available to the language model.
     * 
     * @since 1.0.0
     */
    public static class ColumnDefinition {
        private final String name;
        private final Class<?> type;
        private final String description;

        public ColumnDefinition(String name, Class<?> type, String description) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.type = Objects.requireNonNull(type, "type cannot be null");
            this.description = description;
        }

        // Backward compatibility factory method for string types
        public static ColumnDefinition fromString(String name, String type, String description) {
            return new ColumnDefinition(name, mapStringToClass(Objects.requireNonNull(type, "type cannot be null")), description);
        }

        public String getName() {
            return name;
        }

        public Class<?> getType() {
            return type;
        }

        public String getTypeName() {
            return mapClassToString(type);
        }

        public String getDescription() {
            return description;
        }

        public String[] getEnumValueNames() {
            if (!type.isEnum()) return null;
            return Arrays.stream(type.getEnumConstants())
                         .map(Object::toString)
                         .toArray(String[]::new);
        }

        public boolean isEnum() {
            return type.isEnum();
        }

        private static Class<?> mapStringToClass(String typeString) {
            return switch (typeString.toLowerCase()) {
                case "string" -> String.class;
                case "number", "integer", "int" -> Number.class;
                case "boolean", "bool" -> Boolean.class;
                case "date" -> java.time.LocalDate.class;
                case "datetime" -> java.time.LocalDateTime.class;
                default -> String.class; // Default fallback
            };
        }

        private static String mapClassToString(Class<?> clazz) {
            if (clazz.isEnum()) {
                return "enum";
            } else if (clazz == String.class) {
                return "string";
            } else if (Number.class.isAssignableFrom(clazz) || clazz == int.class || clazz == double.class || clazz == long.class || clazz == float.class) {
                return "number";
            } else if (clazz == Boolean.class || clazz == boolean.class) {
                return "boolean";
            } else if (clazz == java.time.LocalDate.class) {
                return "date";
            } else if (clazz == java.time.LocalDateTime.class) {
                return "datetime";
            } else {
                return "string"; // Default fallback
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ColumnDefinition that = (ColumnDefinition) o;
            return Objects.equals(name, that.name) &&
                   Objects.equals(type, that.type) &&
                   Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, description);
        }

        @Override
        public String toString() {
            return "ColumnDefinition{" +
                    "name='" + name + '\'' +
                    ", type=" + type.getSimpleName() +
                    ", description='" + description + '\'' +
                    (isEnum() ? ", enumValues=" + Arrays.toString(getEnumValueNames()) : "") +
                    '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String name;
            private Class<?> type;
            private String description;

            public Builder name(String name) {
                ensureNotBlank(name, "name");
                this.name = name;
                return this;
            }

            public Builder type(Class<?> type) {
                Objects.requireNonNull(type, "type cannot be null");
                this.type = type;
                return this;
            }

            public Builder type(String type) {
                ensureNotBlank(type, "type");
                this.type = mapStringToClass(type);
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public ColumnDefinition build() {
                ensureNotBlank(name, "name");
                Objects.requireNonNull(type, "type cannot be null");
                return new ColumnDefinition(name, type, description);
            }
        }
    }
}