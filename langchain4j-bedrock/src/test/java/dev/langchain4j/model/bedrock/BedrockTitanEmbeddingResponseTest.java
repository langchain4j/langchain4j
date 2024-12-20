package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockTitanEmbeddingResponseTest {

    @Test
    void testToEmbeddingsWithEmptyEmbeddings() {
        // given
        BedrockTitanEmbeddingResponse response = new BedrockTitanEmbeddingResponse();
        response.setEmbedding(new float[0]);
        response.setInputTextTokenCount(0);

        // when
        List<Embedding> result = response.toEmbeddings();

        // then
        //~ There should be one empty embedding as the result
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).vector().length);
    }

    @Test
    void testToEmbeddingsWithGivenEmbeddings() {
        // given
        float[] testEmbedding = {0.1f, 0.2f, 0.3f};
        BedrockTitanEmbeddingResponse response = new BedrockTitanEmbeddingResponse();
        response.setEmbedding(testEmbedding);
        response.setInputTextTokenCount(0);

        // when
        List<Embedding> result = response.toEmbeddings();

        // then
        //~ There should be one embedding initialized with 3 vector elements
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).vector().length);
    }

}
