package dev.langchain4j.model.vertexai;

class VertexAiEmbeddingParameters {
    private Integer outputDimensionality;
    private Boolean autoTruncate;

    VertexAiEmbeddingParameters(Integer outputDimensionality, Boolean autoTruncate) {
        this.outputDimensionality = outputDimensionality;
        this.autoTruncate = autoTruncate;
    }

    public Integer getOutputDimensionality() {
        return outputDimensionality;
    }

    public Boolean isAutoTruncate() {
        return autoTruncate;
    }
}