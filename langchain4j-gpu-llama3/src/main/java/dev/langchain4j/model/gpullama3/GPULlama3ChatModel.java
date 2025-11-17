package dev.langchain4j.model.gpullama3;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.requireNonNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.nio.file.Path;

/**
 * GPULlama3 implementation of the langchain4j ChatModel interface.
 * <p>
 * This model provides synchronous chat capabilities using the GPULlama3.java library,
 * supporting both CPU and GPU execution modes. The model automatically separates thinking content from actual responses.
 *
 * <p>Example usage:
 * <pre>{@code
 * GPULlama3ChatModel model = GPULlama3ChatModel.builder()
 *     .modelPath(Paths.get("path/to/model.gguf"))
 *     .temperature(0.7)
 *     .maxTokens(2048)
 *     .onGPU(true)
 *     .build();
 *
 * ChatResponse response = model.chat(chatRequest);
 * }</pre>
 */
public class GPULlama3ChatModel extends GPULlama3BaseModel implements ChatModel {

    // @formatter:off
    private GPULlama3ChatModel(Builder builder) {
        init(
                requireNonNull(builder.modelPath, "modelPath is required and must be specified"),
                getOrDefault(builder.temperature, 0.1),
                getOrDefault(builder.topP, 1.0),
                getOrDefault(builder.seed, 12345),
                getOrDefault(builder.maxTokens, 512),
                getOrDefault(builder.onGPU, Boolean.TRUE));
    }
    // @formatter:on

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        try {
            // Generate a raw response from the model
            String rawResponse = modelResponse(chatRequest, null);

            // Parse thinking and actual response using the GPULlama3ResponseParser
            GPULlama3ResponseParser.ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(rawResponse);

            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate response from GPULlama3", e);
        }
    }

    public static class Builder {

        protected Path modelPath;
        protected Double temperature;
        protected Double topP;
        protected Integer seed;
        protected Integer maxTokens;
        protected Boolean onGPU;

        public Builder() {
            // This is public so it can be extended
        }

        public Builder modelPath(Path modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public Builder onGPU(Boolean onGPU) {
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
