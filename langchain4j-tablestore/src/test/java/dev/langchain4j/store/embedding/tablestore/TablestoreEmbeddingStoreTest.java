package dev.langchain4j.store.embedding.tablestore;

import static dev.langchain4j.store.embedding.tablestore.TablestoreUtils.embeddingToString;
import static dev.langchain4j.store.embedding.tablestore.TablestoreUtils.parseEmbeddingString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.document.Metadata;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TablestoreEmbeddingStoreTest {

    @Test
    void parse_embedding_string() {
        float[] floats = parseEmbeddingString("   [1,2,3,4,  5.678, 9.12345 ,  -0.0123] ");
        float[] expect = new float[] {1, 2, 3, 4, 5.678f, 9.12345f, -0.0123f};
        assertThat(floats).containsExactly(expect, within(0.000001f));
    }

    @Test
    void embedding_to_string() {
        float[] expect = new float[] {1, 2, 3, 4, 5.678f, 9.12345f, -0.0123f};
        String embeddingToString = embeddingToString(expect);
        assertThat(embeddingToString).isEqualTo("[1.0,2.0,3.0,4.0,5.678,9.12345,-0.0123]");
    }

    @Test
    void testj_supported_value_types() throws Exception {
        Field field = Metadata.class.getDeclaredField("SUPPORTED_VALUE_TYPES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Class<?>> supportedValueTypes = (Set<Class<?>>) field.get(new Metadata());
        assertThat(supportedValueTypes.size())
                .as("when Metadata#SUPPORTED_VALUE_TYPES add new types, we should modify:\n"
                        + "1. write logic: rowToMetadata.\n"
                        + "2. read logic: innerAdd")
                .isEqualTo(10);
    }
}
