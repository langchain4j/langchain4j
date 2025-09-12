package dev.langchain4j.model.huggingface.client;

import java.util.List;

public interface HuggingFaceClient {

    @Deprecated(forRemoval = true, since = "1.2.0-beta8")
    TextGenerationResponse chat(TextGenerationRequest request);

    @Deprecated(forRemoval = true, since = "1.2.0-beta8")
    TextGenerationResponse generate(TextGenerationRequest request);

    List<float[]> embed(EmbeddingRequest request);
}
