package dev.langchain4j.model.huggingface.client;

import java.util.List;

public class EmbeddingRequest {

    private final List<String> inputs;
    private final Options options;

    public EmbeddingRequest(List<String> inputs, boolean waitForModel) {
        this.inputs = inputs;
        this.options = Options.builder()
                .waitForModel(waitForModel)
                .build();
    }

    public List<String> getInputs() {
        return inputs;
    }

    public Options getOptions() {
        return options;
    }
}
