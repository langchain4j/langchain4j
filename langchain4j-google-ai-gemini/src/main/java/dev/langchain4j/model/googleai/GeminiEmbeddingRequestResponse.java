package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchableRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse.GeminibeddingResponseValues;
import java.util.List;

public final class GeminiEmbeddingRequestResponse {
    private GeminiEmbeddingRequestResponse() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiEmbeddingRequest(
            @JsonProperty("model") String model,
            @JsonProperty("content") GeminiContent content,
            @JsonProperty("taskType") GoogleAiEmbeddingModel.TaskType taskType,
            @JsonProperty("title") String title,
            @JsonProperty("outputDimensionality") Integer outputDimensionality) implements BatchableRequest {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiEmbeddingResponse(GeminibeddingResponseValues embedding) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record GeminibeddingResponseValues(List<Float> values) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiBatchEmbeddingRequest(List<GeminiEmbeddingRequest> requests) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiBatchEmbeddingResponse(List<GeminibeddingResponseValues> embeddings) {}
}
