package dev.langchain4j.store.embedding.tablestore;

import static dev.langchain4j.store.embedding.tablestore.TablestoreUtils.embeddingToString;
import static dev.langchain4j.store.embedding.tablestore.TablestoreUtils.parseEmbeddingString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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

    @Test
    void parse_embedding_string_with_empty_array() {
        float[] floats = parseEmbeddingString("[]");
        assertThat(floats).isEmpty();
    }

    @Test
    void parse_embedding_string_with_single_value() {
        float[] floats = parseEmbeddingString("[1.1]");
        assertThat(floats).containsExactly(1.1f);
    }

    @Test
    void parse_embedding_string_with_invalid_format() {
        assertThatThrownBy(() -> parseEmbeddingString("[1.0, a, 1.0]")).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void parse_embedding_string_null_input() {
        assertThatThrownBy(() -> parseEmbeddingString(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void embedding_to_string_empty_array() {
        float[] empty = new float[0];
        String result = embeddingToString(empty);
        assertThat(result).isEqualTo("[]");
    }

    @Test
    void embedding_to_string_null_array() {
        assertThatThrownBy(() -> embeddingToString(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEmbeddingToStringWithEmptyArray() {
        float[] embedding = {};
        String result = embeddingToString(embedding);
        assertThat(result).isEqualTo("[]");
    }

    @Test
    void testEmbeddingToStringWithNullArray() {
        assertThatThrownBy(() -> embeddingToString(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseEmbeddingStringWithEmptyArray() {
        String embeddingString = "[]";
        float[] result = parseEmbeddingString(embeddingString);
        assertThat(result).isEmpty();
    }

    @Test
    void testParseEmbeddingStringWithNullString() {
        assertThatThrownBy(() -> parseEmbeddingString(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
