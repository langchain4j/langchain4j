package dev.langchain4j.model.gpullama3;

import org.beehive.gpullama3.Options;

import java.nio.file.Path;

abstract class GPULlama3BaseModel {
    private Path modelPath;
    private Double temperature;
    private Double topP;
    private Integer seed;
    private Integer maxTokens;
    private boolean onGPU;

    //
    public void init(Path modelPath, Double temperature, Double topP, Integer seed, Integer maxTokens, boolean onGPU) {
        this.maxTokens = maxTokens;
        this.onGPU = onGPU;
        this.modelPath = modelPath;
        this.temperature = temperature;
        this.topP = topP;
        this.seed = seed;
    }



    public static Options getOptions(String userMessage) {
        Options defaultOptions = Options.getDefaultOptions();
        Options opts = new Options(modelPath, userMessage,
                defaultOptions.systemPrompt(),
                defaultOptions.suffix(),
                false /* interactive */,
                defaultOptions.temperature(),
                defaultOptions.topp(),
                defaultOptions.seed(),
                defaultOptions.maxTokens(),
                true,
                defaultOptions.echo(),
                onGPU
        );
        return opts;
    }

}
