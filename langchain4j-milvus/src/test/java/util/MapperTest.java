package util;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.util.Mapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapperTest {

    @Test
    public void testToVectors() {
        List<Embedding> mockEmbeddings = Arrays.asList(
                new Embedding(new float[]{1.0f, 2.0f, 3.0f}),
                new Embedding(new float[]{4.0f, 5.0f, 6.0f}),
                new Embedding(new float[]{7.0f, 8.0f, 9.0f})
        );

        List<List<Float>> expectedOutput = Arrays.asList(
                Arrays.asList(1.0f, 2.0f, 3.0f),
                Arrays.asList(4.0f, 5.0f, 6.0f),
                Arrays.asList(7.0f, 8.0f, 9.0f)
        );

        List<List<Float>> actualOutput = Mapper.toVectors(mockEmbeddings);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testTextSegmentsToScalars() {
        List<TextSegment> mockTextSegments = Arrays.asList(
                new TextSegment("Hello", null),
                new TextSegment("Bonjour", null),
                new TextSegment("Hola", null)
        );

        List<String> expectedOutput = Arrays.asList("Hello", "Bonjour", "Hola");

        List<String> actualOutput = Mapper.textSegmentsToScalars(mockTextSegments);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testToScalarsWithNonEmptyList() {
        List<TextSegment> mockTextSegments = Arrays.asList(
                new TextSegment("Hello", null),
                new TextSegment("Bonjour", null),
                new TextSegment("Hola", null)
        );
        List<String> expectedOutput = Arrays.asList("Hello", "Bonjour", "Hola");

        List<String> actualOutput = Mapper.toScalars(mockTextSegments, 10);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testToScalarsWithEmptyList() {
        List<String> expectedOutput = Arrays.asList("", "", "", "", "", "", "", "", "", "");

        List<String> actualOutput = Mapper.toScalars(Arrays.asList(), 10);
        assertEquals(expectedOutput, actualOutput);
    }


}