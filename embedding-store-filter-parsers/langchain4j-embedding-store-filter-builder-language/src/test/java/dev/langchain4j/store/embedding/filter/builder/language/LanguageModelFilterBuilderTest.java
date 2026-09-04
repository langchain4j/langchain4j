package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LanguageModelFilterBuilderTest {

    @Mock
    private ChatModel mockChatModel;

    private TableDefinition tableDefinition;
    private LanguageModelJsonFilterBuilder filterBuilder;

    @BeforeEach
    void setUp() {
        tableDefinition = new TableDefinition(Arrays.asList(
                new TableDefinition.ColumnDefinition("author", String.class, "The author of the document"),
                new TableDefinition.ColumnDefinition("rating", Number.class, "Rating from 1 to 5"),
                new TableDefinition.ColumnDefinition("category", String.class, "Document category"),
                new TableDefinition.ColumnDefinition("isPublished", Boolean.class, "Publication status")
        ));

        filterBuilder = new LanguageModelJsonFilterBuilder(mockChatModel, tableDefinition);
    }

    @Test
    void testConstructorWithNullChatModel() {
        assertThrows(NullPointerException.class, () -> 
                new LanguageModelJsonFilterBuilder(null, tableDefinition));
    }

    @Test
    void testConstructorWithNullTableDefinition() {
        assertThrows(NullPointerException.class, () -> 
                new LanguageModelJsonFilterBuilder(mockChatModel, null));
    }

    @Test
    void testTableDefinitionCreation() {
        TableDefinition.ColumnDefinition column = new TableDefinition.ColumnDefinition(
                "testColumn", String.class, "Test description");
        
        assertEquals("testColumn", column.getName());
        assertEquals(String.class, column.getType());
        assertEquals("string", column.getTypeName());
        assertEquals("Test description", column.getDescription());
    }

    @Test
    void testTableDefinitionWithNullName() {
        assertThrows(NullPointerException.class, () -> 
                new TableDefinition.ColumnDefinition(null, String.class, "description"));
    }

    @Test
    void testTableDefinitionWithNullType() {
        assertThrows(NullPointerException.class, () -> 
                new TableDefinition.ColumnDefinition("name", null, "description"));
    }

    @Test
    void testTableDefinitionEquality() {
        TableDefinition.ColumnDefinition col1 = new TableDefinition.ColumnDefinition(
                "name", String.class, "desc");
        TableDefinition.ColumnDefinition col2 = new TableDefinition.ColumnDefinition(
                "name", String.class, "desc");
        
        assertEquals(col1, col2);
        assertEquals(col1.hashCode(), col2.hashCode());
    }

    @Test
    void testTableDefinitionToString() {
        TableDefinition.ColumnDefinition column = new TableDefinition.ColumnDefinition(
                "author", String.class, "Document author");
        
        String toString = column.toString();
        assertTrue(toString.contains("author"));
        assertTrue(toString.contains("String"));
        assertTrue(toString.contains("Document author"));
    }

    @Test
    void testBuildColumnsDescription() {
        // This tests the internal method indirectly through the constructor
        assertDoesNotThrow(() -> new LanguageModelJsonFilterBuilder(mockChatModel, tableDefinition));
    }

}