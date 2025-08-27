package dev.langchain4j.store.embedding.filter.builder.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TableDefinitionTest {

    private static ColumnDefinition col(String name, String type) {
        return new ColumnDefinition(name, type);
    }

    private static ColumnDefinition col(String name, String type, String desc) {
        return new ColumnDefinition(name, type, desc);
    }

    static Stream<Arguments> should_consider_equals_when_values_are_equal() {
        return Stream.of(
                Arguments.of(
                        new TableDefinition("table1", "desc1", List.of(col("id", "int"))),
                        new TableDefinition("table1", "desc1", List.of(col("id", "int"))),
                        true),
                Arguments.of(
                        new TableDefinition("table1", "desc1", List.of(col("id", "int", "primary key"))),
                        new TableDefinition("table1", "desc1", List.of(col("id", "int", "primary key"))),
                        true));
    }

    @ParameterizedTest
    @MethodSource
    @DisplayName("should_consider_equals_when_values_are_equal")
    void should_consider_equals_when_values_are_equal(TableDefinition a, TableDefinition b, boolean expected) {
        assertEquals(expected, a.equals(b));
        assertEquals(expected, b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    static Stream<Arguments> should_not_consider_equals_when_values_are_different() {
        return Stream.of(
                Arguments.of(
                        new TableDefinition("table1", "desc1", List.of(col("id", "int"))),
                        new TableDefinition("table2", "desc1", List.of(col("id", "int"))),
                        false),
                Arguments.of(
                        new TableDefinition("table1", "desc1", List.of(col("id", "int"))),
                        new TableDefinition("table1", "desc2", List.of(col("id", "int"))),
                        false),
                Arguments.of(
                        new TableDefinition("table1", "desc1", List.of(col("id", "int"))),
                        new TableDefinition("table1", "desc1", List.of(col("id", "varchar"))),
                        false));
    }

    @ParameterizedTest
    @MethodSource
    @DisplayName("should_not_consider_equals_when_values_are_different")
    void should_not_consider_equals_when_values_are_different(TableDefinition a, TableDefinition b, boolean expected) {
        assertEquals(expected, a.equals(b));
        assertEquals(expected, b.equals(a));
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("should_return_false_when_compared_with_null_or_different_class")
    void should_return_false_when_compared_with_null_or_different_class() {
        TableDefinition def = new TableDefinition("table", "desc", List.of(col("id", "int")));
        assertNotEquals(def, null);
        assertNotEquals(def, "some string");
    }

    @Test
    @DisplayName("should_use_toString_format_correctly")
    void should_use_toString_format_correctly() {
        TableDefinition def = new TableDefinition("table", "desc", List.of(col("id", "int")));
        String output = def.toString();
        assertTrue(output.contains("table"));
        assertTrue(output.contains("desc"));
        assertTrue(output.contains("ColumnDefinition"));
    }

    @Test
    @DisplayName("should_build_using_builder_with_columns")
    void should_build_using_builder_with_columns() {
        TableDefinition def = TableDefinition.builder()
                .name("table")
                .description("desc")
                .addColumn("id", "int")
                .addColumn("name", "string", "username")
                .build();

        assertEquals("table", def.name());
        assertEquals("desc", def.description());
        assertEquals(2, def.columns().size());
    }

    @Test
    @DisplayName("should_throw_exception_when_name_is_blank_or_columns_is_empty")
    void should_throw_exception_when_name_is_blank_or_columns_is_empty() {
        assertThrows(IllegalArgumentException.class, () -> new TableDefinition("", "desc", List.of(col("id", "int"))));
        assertThrows(IllegalArgumentException.class, () -> new TableDefinition("table", "desc", List.of()));
    }
}
