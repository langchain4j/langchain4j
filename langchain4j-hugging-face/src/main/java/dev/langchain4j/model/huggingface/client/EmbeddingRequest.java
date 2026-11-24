package dev.langchain4j.model.huggingface.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
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
