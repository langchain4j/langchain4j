package dev.langchain4j.model.huggingface.client;

import java.util.List;

public interface HuggingFaceClient {

    @Deprecated(forRemoval = true, since = "1.7.0-beta13")
    TextGenerationResponse chat(TextGenerationRequest request);

    @Deprecated(forRemoval = true, since = "1.7.0-beta13")
    TextGenerationResponse generate(TextGenerationRequest request);

    List<float[]> embed(EmbeddingRequest request);
}
