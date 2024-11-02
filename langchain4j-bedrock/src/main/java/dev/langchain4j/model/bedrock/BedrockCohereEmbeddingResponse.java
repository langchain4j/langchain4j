package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.bedrock.internal.BedrockEmbeddingResponse;

import java.util.List;

/**
 * Bedrock Cohere embedding response
 */
class BedrockCohereEmbeddingResponse implements BedrockEmbeddingResponse {

    private String id;

    private List<String> texts;

    private float[] embedding;

    private int inputTextTokenCount;

    @Override
    public Embedding toEmbedding() {
        return new Embedding(embedding);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getTexts() {
        return texts;
    }

    public void setTexts(List<String> texts) {
        this.texts = texts;
    }

    public void addText(String text) {
        if (this.texts == null) {
            this.texts = List.of(text);
            return;
        }
        this.texts.add(text);
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    @Override
    public int getInputTextTokenCount() {
        return inputTextTokenCount;
    }

    public void setInputTextTokenCount(int inputTextTokenCount) {
        this.inputTextTokenCount = inputTextTokenCount;
    }

}
