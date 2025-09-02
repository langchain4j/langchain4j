package dev.langchain4j.model.gpullama3;

import dev.langchain4j.model.chat.ChatModel;
import org.beehive.gpullama3.Options;
import org.beehive.gpullama3.LlamaApp;
import org.beehive.gpullama3.auxiliary.LastRunMetrics;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.model.loader.ModelLoader;

import java.io.IOException;
import java.nio.file.Path;

public class GPULlama3ChatModel implements ChatModel {

    private final Path modelPath;
    private final float temperature;
    private final float topp;
    private final long seed;
    private final int maxTokens;

    private Model model;
    private Sampler sampler;

    private GPULlama3ChatModel(Builder builder) {
        this.modelPath = builder.modelPath;
        this.temperature = builder.temperature;
        this.topp = builder.topp;
        this.seed = builder.seed;
        this.maxTokens = builder.maxTokens;
        try {
            this.model = ModelLoader.loadModel(modelPath, maxTokens, true);
            this.sampler = LlamaApp.selectSampler(
                    model.configuration().vocabularySize(),
                    temperature, topp, seed
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + modelPath, e);
        }
    }

    @Override
    public String chat(String userMessage) {
        Options defaultOptions = Options.getDefaultOptions();
        Options opts = new Options(modelPath,
                userMessage,
               defaultOptions.systemPrompt(),
                defaultOptions.suffix(),
                false /* interactive */,
                defaultOptions.temperature(),
                defaultOptions.topp(),
                defaultOptions.seed(),
                defaultOptions.maxTokens(),
                false,
                defaultOptions.echo());

        return model.runInstructOnce(sampler, opts);
    }

    public void printLastMetrics() {
        LastRunMetrics.printMetrics();
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path modelPath;
        private float temperature = 0.7f;
        private float topp = 1.0f;
        private long seed = 42;
        private int maxTokens = 512;

        public Builder modelPath(Path modelPath) {
            this.modelPath = modelPath;
            return this;
        }
        public Builder temperature(float temperature) {
            this.temperature = temperature;
            return this;
        }
        public Builder topp(float topp) {
            this.topp = topp;
            return this;
        }
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public GPULlama3ChatModel build() {
            if (modelPath == null) throw new IllegalArgumentException("modelPath is required");
            return new GPULlama3ChatModel(this);
        }
    }
}
