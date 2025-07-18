package dev.langchain4j.model.openai.internal.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiEmbeddingDeserializerTest {

    private final OpenAiEmbeddingDeserializer deserializer = new OpenAiEmbeddingDeserializer();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testDeserializeFloatArray() throws Exception {
        JsonParser parser = mock(JsonParser.class);
        DeserializationContext context = mock(DeserializationContext.class);

        when(parser.currentToken()).thenReturn(JsonToken.START_ARRAY);
        List<Float> expected = List.of(1.0f, 2.0f, 3.0f);
        when(parser.readValueAs(any(TypeReference.class))).thenReturn(expected);

        List<Float> result = deserializer.deserialize(parser, context);
        assertEquals(expected, result);
    }

    @Test
    void testDeserializeBase64String() throws Exception {
        List<Float> original = List.of(4.2f, -1.5f, 0.0f);
        ByteBuffer buffer = ByteBuffer.allocate(original.size() * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : original) {
            buffer.putFloat(v);
        }
        String base64 = Base64.getEncoder().encodeToString(buffer.array());

        JsonParser parser = mock(JsonParser.class);
        DeserializationContext context = mock(DeserializationContext.class);

        when(parser.currentToken()).thenReturn(JsonToken.VALUE_STRING);
        when(parser.getValueAsString()).thenReturn(base64);

        List<Float> result = deserializer.deserialize(parser, context);

        assertListFloatEquals(original, result, 0.0001f);
    }

    @Test
    void testDeserializeIllegalToken() {
        JsonParser parser = mock(JsonParser.class);
        DeserializationContext context = mock(DeserializationContext.class);

        when(parser.currentToken()).thenReturn(JsonToken.VALUE_NUMBER_INT);

        IOException e = assertThrows(IOException.class, () -> deserializer.deserialize(parser, context));
        assertTrue(e.getMessage().contains("Illegal embedding"));
    }

    @Test
    void testDeserializeEmbeddingWithFloatArray() throws Exception {
        String json =
                """
                {
                  "index": 1,
                  "embedding": [1.0, 2.0, 3.0]
                }
                """;
        Embedding embedding = mapper.readValue(json, Embedding.class);
        assertEquals(1, embedding.index());
        assertListFloatEquals(List.of(1.0f, 2.0f, 3.0f), embedding.embedding(), 0.0001f);
    }

    @Test
    void testDeserializeEmbeddingWithBase64String() throws Exception {
        List<Float> original = List.of(4.2f, -1.5f, 0.0f);
        ByteBuffer buffer = ByteBuffer.allocate(original.size() * Float.BYTES);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float v : original) buffer.putFloat(v);
        String base64 = Base64.getEncoder().encodeToString(buffer.array());

        String json =
                """
                {
                  "index": 2,
                  "embedding": "%s"
                }
                """
                        .formatted(base64);

        Embedding embedding = mapper.readValue(json, Embedding.class);
        assertEquals(2, embedding.index());
        assertListFloatEquals(original, embedding.embedding(), 0.0001f);
    }

    @Test
    void testDeserializeEmbeddingWithWrongType() {
        String json =
                """
                {
                  "index": 3,
                  "embedding": 123
                }
                """;
        JsonProcessingException ex = assertThrows(JsonProcessingException.class, () -> {
            mapper.readValue(json, Embedding.class);
        });
        assertTrue(ex.getMessage().contains("Illegal embedding"));
    }

    private static void assertListFloatEquals(List<Float> expected, List<Float> actual, float delta) {
        assertEquals(expected.size(), actual.size(), "List size not equal");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), delta, "Float at index " + i + " not equal");
        }
    }
}
