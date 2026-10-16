package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EnumColumnDescriptionTest {

    // Test enums for this test class
    enum TestCategory { TECHNOLOGY, SCIENCE, BUSINESS }
    enum TestStatus { DRAFT, REVIEW, PUBLISHED, ARCHIVED }

    @Test
    void testBuildColumnsDescriptionWithEnums() throws Exception {
        // Create a mock ChatModel
        ChatModel mockChatModel = Mockito.mock(ChatModel.class);
        
        // Create a table definition with enum columns
        TableDefinition tableDefinition = new TableDefinition(Arrays.asList(
                new TableDefinition.ColumnDefinition("author", String.class, "The author of the document"),
                new TableDefinition.ColumnDefinition("category", TestCategory.class, "The category or topic"),
                new TableDefinition.ColumnDefinition("status", TestStatus.class, "Document status"),
                new TableDefinition.ColumnDefinition("rating", Number.class, "Rating from 1 to 5")
        ));
        
        // Create the filter builder
        LanguageModelJsonFilterBuilder filterBuilder = new LanguageModelJsonFilterBuilder(mockChatModel, tableDefinition);
        
        // Use reflection to call the private buildColumnsDescription method
        Method method = LanguageModelJsonFilterBuilder.class.getDeclaredMethod("buildColumnsDescription");
        method.setAccessible(true);
        String columnsDescription = (String) method.invoke(filterBuilder);
        
        // Verify the description includes enum values
        assertTrue(columnsDescription.contains("- author (string): The author of the document"));
        assertTrue(columnsDescription.contains("- category (enum): The category or topic [possible values: TECHNOLOGY, SCIENCE, BUSINESS]"));
        assertTrue(columnsDescription.contains("- status (enum): Document status [possible values: DRAFT, REVIEW, PUBLISHED, ARCHIVED]"));
        assertTrue(columnsDescription.contains("- rating (number): Rating from 1 to 5"));
        
        // Verify format
        String[] lines = columnsDescription.split("\n");
        assertEquals(4, lines.length);
    }

    @Test
    void testBuildColumnsDescriptionWithoutEnums() throws Exception {
        // Create a mock ChatModel
        ChatModel mockChatModel = Mockito.mock(ChatModel.class);
        
        // Create a table definition without enum columns
        TableDefinition tableDefinition = new TableDefinition(Arrays.asList(
                new TableDefinition.ColumnDefinition("author", String.class, "The author"),
                new TableDefinition.ColumnDefinition("rating", Number.class, "Rating from 1 to 5")
        ));
        
        // Create the filter builder
        LanguageModelJsonFilterBuilder filterBuilder = new LanguageModelJsonFilterBuilder(mockChatModel, tableDefinition);
        
        // Use reflection to call the private buildColumnsDescription method
        Method method = LanguageModelJsonFilterBuilder.class.getDeclaredMethod("buildColumnsDescription");
        method.setAccessible(true);
        String columnsDescription = (String) method.invoke(filterBuilder);
        
        // Verify no enum descriptions are present
        assertFalse(columnsDescription.contains("[possible values:"));
        assertTrue(columnsDescription.contains("- author (string): The author"));
        assertTrue(columnsDescription.contains("- rating (number): Rating from 1 to 5"));
    }
}