package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockCohereEmbeddingResponseTest {

    @Test
    void testToEmbeddingEmpty() {
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setEmbeddings(new float[0][0]);
        assertNotNull(response.toEmbeddings());
        assertTrue(response.toEmbeddings().isEmpty());
    }


    @Test
    void testToEmbeddingNotNull() {
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setEmbeddings(new float[][]{{1.0f, 2.0f, 3.0f}});
        assertNotNull(response.toEmbeddings());
        assertEquals(3, ((Embedding)response.toEmbeddings().get(0)).vector().length);

        response.setEmbeddings(new float[][]{{1.0f, 1.0f, 1.0f}, {2.0f, 2.0f, 2.0f}});

        assertEquals(2, response.toEmbeddings().size());
        assertEquals(1.0f, ((Embedding)response.toEmbeddings().get(0)).vector()[0]);
        assertEquals(2.0f, ((Embedding)response.toEmbeddings().get(1)).vector()[0]);
    }

    @Test
    void testGetEmbeddingsNotNull() {
        float[][] responseEmbeddings = new float[][]{{1.0f, 2.0f, 3.0f}};
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setEmbeddings(responseEmbeddings);
        assertNotNull(response.getEmbeddings());
        assertEquals(responseEmbeddings, response.getEmbeddings());
    }

    @Test
    void testGetIdNotNull() {
        String responseId = UUID.randomUUID().toString();
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setId(responseId);
        assertNotNull(response.getId());
        assertEquals(responseId, response.getId());
    }

    @Test
    void testGetTextNotNull() {
        List<String> responseTexts = List.of("one", "two");
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setTexts(responseTexts);
        assertNotNull(response.getTexts());
        assertEquals(responseTexts, response.getTexts());
    }

    @Test
    void testGetInputTokensNotNull() {
        Integer responseInputTokens = 1;
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        response.setInputTextTokenCount(responseInputTokens);
        assertEquals(responseInputTokens, response.getInputTextTokenCount());
    }

    @Test
    void testGetInputTokensDefault() {
        BedrockCohereEmbeddingResponse response = new BedrockCohereEmbeddingResponse();
        assertEquals(0, response.getInputTextTokenCount());
    }

}
