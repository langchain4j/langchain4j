package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
class GoogleAiEmbeddingRequest {
    String model;
    GeminiContent content;
    GoogleAiEmbeddingModel.TaskType taskType;
    String title;
    Integer outputDimensionality;
}
