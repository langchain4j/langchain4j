package dev.langchain4j.model.bedrock;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BedrockCohereEmbeddingResponseTest {

    @Test
    void testToEmbedding_null() {
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setEmbeddings(null);
        assertNull(response.toEmbedding());
    }

    @Test
    void testToEmbedding_notNull() {
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setEmbeddings(new float[][]{{1.0f, 2.0f, 3.0f}});
        assertNotNull(response.toEmbedding());
        assertEquals(1.0f, response.toEmbedding().vector()[0]);
    }

    @Test
    void testGetEmbeddings_notNull() {
        float[][] responseEmbeddings = new float[][]{{1.0f, 2.0f, 3.0f}};
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setEmbeddings(responseEmbeddings);
        assertNotNull(response.getEmbeddings());
        assertEquals(responseEmbeddings, response.getEmbeddings());
    }

    @Test
    void testGetId_notNull() {
        String responseId = UUID.randomUUID().toString();
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setId(responseId);
        assertNotNull(response.getId());
        assertEquals(responseId, response.getId());
    }

    @Test
    void testGetText_notNull() {
        List<String> responseTexts = List.of("one", "two");
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setTexts(responseTexts);
        assertNotNull(response.getTexts());
        assertEquals(responseTexts, response.getTexts());
    }

    @Test
    void testGetInputTokens_notNull() {
        Integer responseInputTokens = 1;
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setInputTextTokenCount(responseInputTokens);
        assertEquals(responseInputTokens, response.getInputTextTokenCount());
    }

    @Test
    void testGetInputTokens_default() {
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        assertEquals(0, response.getInputTextTokenCount());
    }

}
