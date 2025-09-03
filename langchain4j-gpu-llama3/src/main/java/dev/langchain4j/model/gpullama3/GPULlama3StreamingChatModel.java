package dev.langchain4j.model.gpullama3;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.beehive.gpullama3.LlamaApp;
import org.beehive.gpullama3.Options;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.loader.ModelLoader;

import java.io.IOException;
import java.nio.file.Path;

public class GPULlama3StreamingChatModel implements StreamingChatModel {
    private final Path modelPath;
    private final float temperature;
    private final float topp;
    private final long seed;
    private final int maxTokens;
    private final boolean onGPU;
    private Model model;
    private Sampler sampler;

    private GPULlama3StreamingChatModel(Builder builder) {
        this.modelPath = builder.modelPath;
        this.temperature = builder.temperature;
        this.topp = builder.topp;
        this.seed = builder.seed;
        this.maxTokens = builder.maxTokens;
        this.onGPU = builder.onGPU;
        try {
            this.model = ModelLoader.loadModel(modelPath, maxTokens, true);
            this.sampler = LlamaApp.selectSampler(model.configuration().vocabularySize(), temperature, topp, seed);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + modelPath, e);
        }
    }

    @Override
    public void chat(String userMessage, StreamingChatResponseHandler handler) {
        Options opts = getOptions(userMessage);

        String finalResponse = model.runInstructOnceLangChain4J(
                sampler,
                opts,
                token -> handler.onPartialResponse(token));

        ChatResponse chatResponse  = ChatResponse.builder().aiMessage(AiMessage.from(finalResponse)).build();
        handler.onCompleteResponse(chatResponse);
    }

    private Options getOptions(String userMessage) {
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path modelPath;
        private float temperature = 0.7f;
        private float topp = 1.0f;
        private long seed = 42;
        private int maxTokens = 1024;
        private boolean onGPU = true;
        
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

        public Builder onGPU(boolean onGPU) {
            this.onGPU = onGPU;
            return this;
        }

        public GPULlama3StreamingChatModel build() {
            if (modelPath == null) {
                throw new IllegalArgumentException("modelPath is required");
            }
            return new GPULlama3StreamingChatModel(this);
        }
    }

}
