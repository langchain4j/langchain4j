package dev.langchain4j.model.gpullama3;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.requireNonNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.nio.file.Path;

/**
 * GPULlama3 implementation of the langchain4j StreamingChatModel interface.
 * <p>
 * This model provides synchronous chat capabilities using the GPULlama3.java library,
 * supporting both CPU and GPU execution modes. The model automatically separates thinking content from actual responses.
 *
 * <p>Example usage:
 * <pre>{@code
 * GPULlama3StreamingChatModel model = GPULlama3StreamingChatModel.builder()
 *     .modelPath(Paths.get("path/to/model.gguf"))
 *     .temperature(0.7)
 *     .maxTokens(2048)
 *     .onGPU(true)
 *     .build();
 *
 * ChatResponse response = model.chat(chatRequest);
 * }</pre>
 */
public class GPULlama3StreamingChatModel extends GPULlama3BaseModel implements StreamingChatModel {

    // @formatter:off
    private GPULlama3StreamingChatModel(Builder builder) {
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
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        try {
            // Create streaming parser using the utility class
            GPULlama3ResponseParser.StreamingParser parser =
                    GPULlama3ResponseParser.createStreamingParser(handler, getModel());

            // Generate response with streaming callback
            String rawResponse = modelResponse(chatRequest, parser::onToken);

            // Parse the complete response and send final result
            GPULlama3ResponseParser.ParsedResponse parsed = GPULlama3ResponseParser.parseResponse(rawResponse);

            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .build();

            handler.onCompleteResponse(chatResponse);
        } catch (Exception e) {
            handler.onError(e);
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

        /**
         * Sets the path to the GGUF model file.
         *
         * @param modelPath the path to the model file
         * @return {@code this}
         */
        public Builder modelPath(Path modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        /**
         * Controls whether to run inference on the GPU. Defaults to {@code true}.
         *
         * @param onGPU {@code true} to use GPU, {@code false} for CPU
         * @return {@code this}
         */
        public Builder onGPU(Boolean onGPU) {
            this.onGPU = onGPU;
            return this;
        }

        /**
         * Sets the sampling temperature. Defaults to {@code 0.1}.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the nucleus sampling probability. Defaults to {@code 1.0}.
         *
         * @param topP the top-P value
         * @return {@code this}
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the maximum number of tokens to generate. Defaults to {@code 512}.
         *
         * @param maxTokens the maximum number of tokens
         * @return {@code this}
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the random seed for deterministic output. Defaults to {@code 12345}.
         *
         * @param seed the random seed
         * @return {@code this}
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public GPULlama3StreamingChatModel build() {
            return new GPULlama3StreamingChatModel(this);
        }
    }
}
