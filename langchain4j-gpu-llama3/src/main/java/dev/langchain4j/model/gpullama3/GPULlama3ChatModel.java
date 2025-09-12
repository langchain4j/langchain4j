package dev.langchain4j.model.gpullama3;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.beehive.gpullama3.Options;
import org.beehive.gpullama3.LlamaApp;
import org.beehive.gpullama3.auxiliary.LastRunMetrics;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.model.loader.ModelLoader;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class GPULlama3ChatModel extends GPULlama3BaseModel implements ChatModel {


    private GPULlama3Provider gpuLlama3Provider;

    private GPULlama3ChatModel(Builder builder) {
        init(
            getOrDefault(builder.modelPath, Options.getDefaultOptions().modelPath()),
            getOrDefault(builder.temperature, Options.getDefaultOptions().temperature()),
            getOrDefault(builder.topP, Options.getDefaultOptions().topp()),
            getOrDefault(builder.seed, Options.getDefaultOptions().seed()),
            getOrDefault(builder.maxTokens, Options.getDefaultOptions().maxTokens()),
            getOrDefault(builder.onGPU, true)

        );


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
                defaultOptions.echo(),
                onGPU);

        return gpuLlama3Provider.getModel().runInstructOnce(gpuLlama3Provider.getSampler(), opts);
    }


    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        // Extract and validate parameters
        ChatRequestParameters params = chatRequest.parameters();

        // Convert messages to a single prompt string
        String prompt = chatRequest.messages().getFirst().toString();

        String prompt = convertMessagesToPrompt(chatRequest.messages());

        // Extract parameters with fallbacks to instance defaults
        double requestTemperature = params.temperature() != null ? params.temperature() : this.temperature;
        float requestTopP = params.topP() != null ? params.topP().floatValue() : this.topP;
        int requestMaxTokens = params.maxOutputTokens() != null ? params.maxOutputTokens() : this.maxTokens;

        // Get system prompt from messages or use default
        String effectiveSystemPrompt = extractSystemPrompt(chatRequest.messages());
        if (effectiveSystemPrompt == null) {
            effectiveSystemPrompt = getOrDefault(this.systemPrompt, Options.getDefaultOptions().systemPrompt());
        }

        // Create options for the underlying library
        Options defaultOptions = Options.getDefaultOptions();
        Options opts = new Options(
                modelPath,
                prompt,
                effectiveSystemPrompt,
                defaultOptions.suffix(),
                false, /* interactive */
                requestTemperature,
                requestTopP,
                seed,
                requestMaxTokens,
                false,
                defaultOptions.echo(),
                onGPU
        );

        try {
            // Generate response using the model
            String responseText = model.runInstructOnce(sampler, opts);

            // Create AI message from response
            AiMessage aiMessage = AiMessage.from(responseText);

            // Create and return chat response
            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate response from GPULlama3", e);
        }
    }

    public void printLastMetrics() {
        LastRunMetrics.printMetrics();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        protected Path modelPath;
        protected Double temperature;
        protected Double topP;
        protected Integer seed;
        protected Integer maxTokens;
        protected Boolean onGPU;
        protected String modelName;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder modelPath(Path modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }


        public Builder onGPU (Boolean onGPU) {
            this.onGPU = onGPU;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }


        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }


        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }


        public GPULlama3ChatModel build() {
            return new GPULlama3ChatModel(this);
        }
    }

}
