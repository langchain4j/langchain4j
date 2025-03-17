package dev.langchain4j.store.embedding.tablestore;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static dev.langchain4j.store.embedding.tablestore.TablestoreUtils.embeddingToString;
import static dev.langchain4j.store.embedding.tablestore.TablestoreUtils.parseEmbeddingString;


class TablestoreEmbeddingStoreTest {

    @Test
    void test_parseEmbeddingString() {
        float[] floats = parseEmbeddingString("   [1,2,3,4,  5.678, 9.12345 ,  -0.0123] ");
        float[] expect = new float[]{1, 2, 3, 4, 5.678f, 9.12345f, -0.0123f};
        Assertions.assertArrayEquals(expect, floats, 0.000001f);
    }

    @Test
    void test_embeddingToString() {
        float[] expect = new float[]{1, 2, 3, 4, 5.678f, 9.12345f, -0.0123f};
        String embeddingToString = embeddingToString(expect);
        Assertions.assertEquals("[1.0,2.0,3.0,4.0,5.678,9.12345,-0.0123]", embeddingToString);
    }

    @Test
    void testj_supported_value_types() throws Exception {
        Field field = Metadata.class.getDeclaredField("SUPPORTED_VALUE_TYPES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Class<?>> supportedValueTypes = (Set<Class<?>>) field.get(new Metadata());
        Assertions.assertEquals(10, supportedValueTypes.size(), "when Metadata#SUPPORTED_VALUE_TYPES add new types, we should modify:\n" +
                "1. write logic: rowToMetadata.\n" +
                "2. read logic: innerAdd");
    }
}