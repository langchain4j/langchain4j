package dev.langchain4j.model.gpullama3;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
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

    private Model model;
    private Sampler sampler;

    private GPULlama3StreamingChatModel(Builder builder) {
        this.modelPath = builder.modelPath;
        this.temperature = builder.temperature;
        this.topp = builder.topp;
        this.seed = builder.seed;
        this.maxTokens = builder.maxTokens;
        try {
            this.model = ModelLoader.loadModel(modelPath, maxTokens, true);
            this.sampler = LlamaApp.selectSampler(model.configuration().vocabularySize(), temperature, topp, seed);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + modelPath, e);
        }
    }

    @Override
    public void chat(String userMessage, StreamingChatResponseHandler handler) {

//        ChatRequest chatRequest = ChatRequest.builder()
//                .messages(UserMessage.from(userMessage))
//                .build();

//        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
//        ChatRequestParameters parameters = chatRequest.parameters();
//        ChatRequestValidationUtils.validateParameters(parameters);
//        ChatRequestValidationUtils.validate(parameters.toolChoice());
//        ChatRequestValidationUtils.validate(parameters.responseFormat());


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
                true,
                defaultOptions.echo());

        model.runInstructOnce(sampler,opts);
        handler.onCompleteResponse(null); // TODO: pass actual re
//        chat(chatRequest, handler);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());


        Options defaultOptions = Options.getDefaultOptions();
        Options opts = new Options(modelPath,
                "",
                defaultOptions.systemPrompt(),
                defaultOptions.suffix(),
                false /* interactive */,
                defaultOptions.temperature(),
                defaultOptions.topp(),
                defaultOptions.seed(),
                defaultOptions.maxTokens(),
                true,
                defaultOptions.echo());

        model.runInstructOnce(sampler,opts);
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

        public GPULlama3StreamingChatModel build() {
            if (modelPath == null) {
                throw new IllegalArgumentException("modelPath is required");
            }
            return new GPULlama3StreamingChatModel(this);
        }
    }

}
