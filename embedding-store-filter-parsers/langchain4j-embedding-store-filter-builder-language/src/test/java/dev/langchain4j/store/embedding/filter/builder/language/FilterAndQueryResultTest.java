package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FilterAndQueryResultTest {

    @Test
    void testConstructorAndGetters() {
        IsEqualTo filter = new IsEqualTo("author", "John Doe");
        String modifiedQuery = "machine learning algorithms";
        
        FilterResult result = new FilterResult(filter, modifiedQuery);
        
        assertEquals(filter, result.getFilter());
        assertEquals(modifiedQuery, result.getModifiedQuery());
    }
    
    @Test
    void testConstructorWithNullFilter() {
        String modifiedQuery = "artificial intelligence";
        
        FilterResult result = new FilterResult(null, modifiedQuery);
        
        assertNull(result.getFilter());
        assertEquals(modifiedQuery, result.getModifiedQuery());
    }
    
    @Test
    void testConstructorThrowsOnNullQuery() {
        IsEqualTo filter = new IsEqualTo("category", "TECHNOLOGY");
        
        assertThrows(NullPointerException.class, () -> {
            new FilterResult(filter, null);
        });
    }
    
    @Test
    void testEquals() {
        IsEqualTo filter1 = new IsEqualTo("author", "John Doe");
        IsEqualTo filter2 = new IsEqualTo("author", "John Doe");
        String query = "machine learning";
        
        FilterResult result1 = new FilterResult(filter1, query);
        FilterResult result2 = new FilterResult(filter2, query);
        FilterResult result3 = new FilterResult(null, query);
        FilterResult result4 = new FilterResult(null, "different query");
        
        assertEquals(result1, result2);
        assertNotEquals(result1, result3);
        assertNotEquals(result3, result4);
    }
    
    @Test
    void testHashCode() {
        IsEqualTo filter = new IsEqualTo("author", "John Doe");
        String query = "machine learning";
        
        FilterResult result1 = new FilterResult(filter, query);
        FilterResult result2 = new FilterResult(filter, query);
        
        assertEquals(result1.hashCode(), result2.hashCode());
    }
    
    @Test
    void testToString() {
        IsEqualTo filter = new IsEqualTo("rating", 5);
        String query = "neural networks";
        
        FilterResult result = new FilterResult(filter, query);
        String toString = result.toString();
        
        assertTrue(toString.contains("FilterResult"));
        assertTrue(toString.contains("filter="));
        assertTrue(toString.contains("modifiedQuery="));
        assertTrue(toString.contains(query));
    }
}