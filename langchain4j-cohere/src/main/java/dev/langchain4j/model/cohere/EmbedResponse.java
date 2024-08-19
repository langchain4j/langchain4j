package dev.langchain4j.model.cohere;

import lombok.Getter;

@Getter
class EmbedResponse {

    private String id;
    private String[] texts;
    private float[][] embeddings;
    private Meta meta;
}