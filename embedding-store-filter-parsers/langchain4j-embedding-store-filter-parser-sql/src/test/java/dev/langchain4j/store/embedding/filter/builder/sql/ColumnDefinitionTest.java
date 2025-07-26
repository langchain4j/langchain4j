package dev.langchain4j.store.embedding.filter.builder.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ColumnDefinitionTest {

    private static ColumnDefinition col(String name, String type) {
        return new ColumnDefinition(name, type);
    }

    private static ColumnDefinition col(String name, String type, String desc) {
        return new ColumnDefinition(name, type, desc);
    }

    static Stream<Arguments> should_consider_equals_when_values_are_equal() {
        return Stream.of(
                Arguments.of(col("id", "int"), col("id", "int"), true),
                Arguments.of(col("id", "int", "primary key"), col("id", "int", "primary key"), true),
                Arguments.of(col("name", "string", null), col("name", "string", null), true));
    }

    @ParameterizedTest
    @MethodSource
    @DisplayName("should_consider_equals_when_values_are_equal")
    void should_consider_equals_when_values_are_equal(ColumnDefinition a, ColumnDefinition b, boolean expected) {
        assertEquals(expected, a.equals(b));
        assertEquals(expected, b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    static Stream<Arguments> should_not_consider_equals_when_values_are_different() {
        return Stream.of(
                Arguments.of(col("id", "int"), col("ID", "int"), false),
                Arguments.of(col("id", "int"), col("id", "varchar"), false),
                Arguments.of(col("id", "int", "desc1"), col("id", "int", "desc2"), false),
                Arguments.of(col("id", "int", null), col("id", "int", "desc"), false));
    }

    @ParameterizedTest
    @MethodSource
    @DisplayName("should_not_consider_equals_when_values_are_different")
    void should_not_consider_equals_when_values_are_different(
            ColumnDefinition a, ColumnDefinition b, boolean expected) {
        assertEquals(expected, a.equals(b));
        assertEquals(expected, b.equals(a));
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("should_return_false_when_compared_with_null_or_different_class")
    void should_return_false_when_compared_with_null_or_different_class() {
        ColumnDefinition col = col("id", "int");
        assertNotEquals(null, col);
        assertNotEquals("not a column", col);
    }

    @Test
    @DisplayName("should_use_toString_format_correctly")
    void should_use_toString_format_correctly() {
        ColumnDefinition col = col("id", "int", "primary key");
        String output = col.toString();
        assertTrue(output.contains("id"));
        assertTrue(output.contains("int"));
        assertTrue(output.contains("primary key"));
        assertTrue(output.startsWith("ColumnDefinition("));
    }

    @Test
    @DisplayName("should_throw_exception_when_name_or_type_is_blank")
    void should_throw_exception_when_name_or_type_is_blank() {
        assertThrows(IllegalArgumentException.class, () -> new ColumnDefinition("", "int"));
        assertThrows(IllegalArgumentException.class, () -> new ColumnDefinition("id", ""));
    }
}
