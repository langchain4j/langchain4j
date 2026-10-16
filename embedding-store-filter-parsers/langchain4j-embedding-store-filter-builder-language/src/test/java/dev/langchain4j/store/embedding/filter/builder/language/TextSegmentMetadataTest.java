package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify TextSegment creation with metadata works correctly.
 */
public class TextSegmentMetadataTest {

    @Test
    public void testTextSegmentCreationWithMetadata() {
        // Test data
        String text = "This is a sample text segment for testing.";
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("title", "Test Document");
        metadataMap.put("author", "Test Author");
        metadataMap.put("rating", 5);
        metadataMap.put("isPublished", "true"); // Use String instead of Boolean

        // Create metadata object from map
        Metadata metadata = new Metadata(metadataMap);
        
        // Create TextSegment with metadata using the correct API
        TextSegment segment = TextSegment.from(text, metadata);

        // Verify the segment was created correctly
        assertNotNull(segment);
        assertEquals(text, segment.text());
        assertNotNull(segment.metadata());
        
        // Verify metadata content
        assertEquals("Test Document", segment.metadata().getString("title"));
        assertEquals("Test Author", segment.metadata().getString("author"));
        assertEquals(5, segment.metadata().getInteger("rating"));
        assertEquals("true", segment.metadata().getString("isPublished"));
    }

    @Test
    public void testTextSegmentCreationWithoutMetadata() {
        String text = "This is a simple text segment.";
        
        // Create TextSegment without metadata
        TextSegment segment = TextSegment.from(text);
        
        // Verify the segment was created correctly
        assertNotNull(segment);
        assertEquals(text, segment.text());
        assertNotNull(segment.metadata());
        assertTrue(segment.metadata().toMap().isEmpty());
    }

    @Test
    public void testTextSegmentFactoryMethods() {
        String text = "Test text";
        Map<String, Object> metadataMap = Map.of("key", "value");
        Metadata metadata = new Metadata(metadataMap);
        
        // Test both factory methods
        TextSegment segment1 = TextSegment.from(text, metadata);
        TextSegment segment2 = TextSegment.textSegment(text, metadata);
        
        // Both should create equivalent segments
        assertEquals(segment1, segment2);
        assertEquals(segment1.text(), segment2.text());
        assertEquals(segment1.metadata().toMap(), segment2.metadata().toMap());
    }
}