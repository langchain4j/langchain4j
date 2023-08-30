package dev.langchain4j.model.vertexai;

class VertexAiParameters {

    private final Double temperature;
    private final Integer maxOutputTokens;
    private final Integer topK;
    private final Double topP;

    VertexAiParameters(Double temperature,
                       Integer maxOutputTokens,
                       Integer topK,
                       Double topP) {
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.topK = topK;
        this.topP = topP;
    }
}
