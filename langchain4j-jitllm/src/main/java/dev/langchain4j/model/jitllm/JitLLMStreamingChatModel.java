package dev.langchain4j.model.jitllm;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.requireNonNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.nio.file.Path;
import java.util.List;

/**
 * JitLLM implementation of the langchain4j StreamingChatModel interface.
 * <p>
 * This model provides streaming chat capabilities using the jitLLM library,
 * supporting both CPU and GPU execution modes. The model automatically separates thinking content from actual responses.
 *
 * <p>Example usage:
 * <pre>{@code
 * JitLLMStreamingChatModel model = JitLLMStreamingChatModel.builder()
 *     .modelPath(Paths.get("path/to/model.gguf"))
 *     .temperature(0.7)
 *     .maxTokens(2048)
 *     .onGPU(true)
 *     .build();
 *
 * model.chat(chatRequest, handler);
 * }</pre>
 */
public class JitLLMStreamingChatModel extends JitLLMBaseModel implements StreamingChatModel {

    // @formatter:off
    private JitLLMStreamingChatModel(Builder builder) {
        init(
                requireNonNull(builder.modelPath, "modelPath is required and must be specified"),
                getOrDefault(builder.temperature, 0.1),
                getOrDefault(builder.topP, 1.0),
                getOrDefault(builder.seed, 12345),
                getOrDefault(builder.maxTokens, 512),
                getOrDefault(builder.onGPU, Boolean.TRUE));
    }
    // @formatter:on

    /**
     * A builder for this model.
     *
     * @return a new builder
     */
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
            if (!chatRequest.toolSpecifications().isEmpty()) {
                completeToolAwareChat(chatRequest, handler);
                return;
            }

            // Create streaming parser using the utility class
            JitLLMResponseParser.StreamingParser parser = JitLLMResponseParser.createStreamingParser(handler);

            // One ordered event per emitted completion token, carrying the id and the text it
            // completed. The parser only needs the text; the ids are there for consumers that do.
            org.beehive.jitllm.api.GenerationResult result = modelResponse(chatRequest, parser::onEvent);
            String rawResponse = result.text();

            // Parse the complete response and send final result
            JitLLMResponseParser.ParsedResponse parsed = JitLLMResponseParser.parseResponse(rawResponse);

            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .finishReason(JitLLMConversions.toLangChain4jFinishReason(result.finishReason()))
                    .build();

            handler.onCompleteResponse(chatResponse);
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    private void completeToolAwareChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        org.beehive.jitllm.api.GenerationResult result = modelResponse(chatRequest, null);
        String rawResponse = result.text();
        List<org.beehive.jitllm.api.ChatContent.ToolCall> toolCalls = result.toolCalls();

        if (!toolCalls.isEmpty()) {
            List<ToolExecutionRequest> toolExecutionRequests = JitLLMConversions.toToolExecutionRequests(toolCalls);
            for (int index = 0; index < toolExecutionRequests.size(); index++) {
                handler.onCompleteToolCall(new CompleteToolCall(index, toolExecutionRequests.get(index)));
            }

            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .toolExecutionRequests(toolExecutionRequests)
                            .build())
                    .finishReason(JitLLMConversions.toLangChain4jFinishReason(result.finishReason()))
                    .build());
            return;
        }

        JitLLMResponseParser.ParsedResponse parsed = JitLLMResponseParser.parseResponse(rawResponse);
        String text = parsed.getActualResponse();
        text.codePoints().mapToObj(Character::toString).forEach(handler::onPartialResponse);
        handler.onCompleteResponse(ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text(text)
                        .thinking(parsed.getThinkingContent())
                        .build())
                .finishReason(JitLLMConversions.toLangChain4jFinishReason(result.finishReason()))
                .build());
    }

    /** Collects the model path and sampling parameters before the engine is loaded. */
    public static class Builder {

        /** The GGUF file to load. */
        protected Path modelPath;
        /** Sampling temperature. */
        protected Double temperature;
        /** Nucleus sampling parameter. */
        protected Double topP;
        /** Sampling seed. */
        protected Integer seed;
        /** Context length, and the default generation budget. */
        protected Integer maxTokens;
        /** Whether to run on an accelerator; the backend follows the installed TornadoVM SDK. */
        protected Boolean onGPU;

        public Builder() {
            // This is public so it can be extended
        }

        /**
         * The GGUF file to load.
         *
         * @param modelPath the value to use
         * @return this builder
         */
        public Builder modelPath(Path modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        /**
         * Whether to run on an accelerator; the backend follows the installed TornadoVM SDK.
         *
         * @param onGPU the value to use
         * @return this builder
         */
        public Builder onGPU(Boolean onGPU) {
            this.onGPU = onGPU;
            return this;
        }

        /**
         * Sampling temperature.
         *
         * @param temperature the value to use
         * @return this builder
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Nucleus sampling parameter.
         *
         * @param topP the value to use
         * @return this builder
         */
        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Context length, and the default generation budget.
         *
         * @param maxTokens the value to use
         * @return this builder
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sampling seed.
         *
         * @param seed the value to use
         * @return this builder
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Builds the model, loading the engine.
         *
         * @return the configured model
         */
        public JitLLMStreamingChatModel build() {
            return new JitLLMStreamingChatModel(this);
        }
    }
}
