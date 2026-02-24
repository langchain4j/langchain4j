package dev.langchain4j.store.embedding.filter.builder.language;

import org.junit.jupiter.api.Test;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TableDefinitionEnumTest {

    // Test enum for this test class
    enum TestStatus { DRAFT, REVIEW, PUBLISHED, ARCHIVED }
    enum TestCategory { TECHNOLOGY, SCIENCE, BUSINESS }
    enum TestPriority { LOW, MEDIUM, HIGH }

    @Test
    void testColumnDefinitionWithoutEnum() {
        TableDefinition.ColumnDefinition column = new TableDefinition.ColumnDefinition(
                "author", String.class, "Document author");
        
        assertEquals("author", column.getName());
        assertEquals(String.class, column.getType());
        assertEquals("string", column.getTypeName());
        assertEquals("Document author", column.getDescription());
        assertNull(column.getEnumValueNames());
        assertFalse(column.isEnum());
    }

    @Test
    void testColumnDefinitionWithEnum() {
        TableDefinition.ColumnDefinition column = new TableDefinition.ColumnDefinition(
                "status", TestStatus.class, "Document status");
        
        assertEquals("status", column.getName());
        assertEquals(TestStatus.class, column.getType());
        assertEquals("enum", column.getTypeName());
        assertEquals("Document status", column.getDescription());
        assertTrue(column.isEnum());
        
        String[] enumValues = column.getEnumValueNames();
        assertNotNull(enumValues);
        assertEquals(4, enumValues.length);
        assertTrue(Arrays.asList(enumValues).contains("DRAFT"));
        assertTrue(Arrays.asList(enumValues).contains("REVIEW"));
        assertTrue(Arrays.asList(enumValues).contains("PUBLISHED"));
        assertTrue(Arrays.asList(enumValues).contains("ARCHIVED"));
    }

    @Test
    void testColumnDefinitionEquality() {
        TableDefinition.ColumnDefinition col1 = new TableDefinition.ColumnDefinition(
                "category", TestCategory.class, "Document category");
        TableDefinition.ColumnDefinition col2 = new TableDefinition.ColumnDefinition(
                "category", TestCategory.class, "Document category");
        
        assertEquals(col1, col2);
        assertEquals(col1.hashCode(), col2.hashCode());
    }

    @Test
    void testColumnDefinitionToString() {
        TableDefinition.ColumnDefinition column = new TableDefinition.ColumnDefinition(
                "priority", TestPriority.class, "Priority level");
        
        String toString = column.toString();
        assertTrue(toString.contains("priority"));
        assertTrue(toString.contains("TestPriority"));
        assertTrue(toString.contains("Priority level"));
        assertTrue(toString.contains("enumValues"));
    }

    @Test
    void testBackwardCompatibilityStringFactory() {
        // Test that string factory method still works for non-enum types
        TableDefinition.ColumnDefinition stringColumn = TableDefinition.ColumnDefinition.fromString(
                "title", "string", "Document title");
        
        assertEquals("title", stringColumn.getName());
        assertEquals(String.class, stringColumn.getType());
        assertEquals("string", stringColumn.getTypeName());
        assertFalse(stringColumn.isEnum());
        
        TableDefinition.ColumnDefinition numberColumn = TableDefinition.ColumnDefinition.fromString(
                "count", "number", "Item count");
        
        assertEquals("count", numberColumn.getName());
        assertEquals(Number.class, numberColumn.getType());
        assertEquals("number", numberColumn.getTypeName());
        assertFalse(numberColumn.isEnum());
    }

    @Test
    void testTypeMappings() {
        // Test various type mappings
        TableDefinition.ColumnDefinition boolColumn = new TableDefinition.ColumnDefinition(
                "active", Boolean.class, "Is active");
        assertEquals("boolean", boolColumn.getTypeName());
        
        TableDefinition.ColumnDefinition dateColumn = new TableDefinition.ColumnDefinition(
                "date", java.time.LocalDate.class, "Event date");
        assertEquals("date", dateColumn.getTypeName());
        
        TableDefinition.ColumnDefinition datetimeColumn = new TableDefinition.ColumnDefinition(
                "timestamp", java.time.LocalDateTime.class, "Event timestamp");
        assertEquals("datetime", datetimeColumn.getTypeName());
    }
}