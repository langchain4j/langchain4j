package dev.langchain4j.model.huggingface.client;

import java.util.List;

public interface HuggingFaceClient {
    TextGenerationResponse chat(TextGenerationRequest request);

    TextGenerationResponse generate(TextGenerationRequest request);

    List<float[]> embed(EmbeddingRequest request);
}
