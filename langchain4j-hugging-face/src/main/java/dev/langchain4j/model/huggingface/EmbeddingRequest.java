package dev.langchain4j.model.huggingface;

import java.util.List;

class EmbeddingRequest {

    private final List<String> inputs;
    private final Options options;

    EmbeddingRequest(List<String> inputs, boolean waitForModel) {
        this.inputs = inputs;
        this.options = Options.builder()
                .waitForModel(waitForModel)
                .build();
    }
}
