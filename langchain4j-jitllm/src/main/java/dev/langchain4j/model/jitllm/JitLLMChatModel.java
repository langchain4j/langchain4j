package dev.langchain4j.model.jitllm;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Objects.requireNonNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.nio.file.Path;
import java.util.List;
import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.GenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JitLLM implementation of the langchain4j ChatModel interface.
 * <p>
 * This model provides synchronous chat capabilities using the jitLLM library,
 * supporting both CPU and GPU execution modes. The model automatically separates thinking content from actual responses.
 *
 * <p>Example usage:
 * <pre>{@code
 * JitLLMChatModel model = JitLLMChatModel.builder()
 *     .modelPath(Paths.get("path/to/model.gguf"))
 *     .temperature(0.7)
 *     .maxTokens(2048)
 *     .onGPU(true)
 *     .build();
 *
 * ChatResponse response = model.chat(chatRequest);
 * }</pre>
 */
public class JitLLMChatModel extends JitLLMBaseModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(JitLLMChatModel.class);

    // @formatter:off
    private JitLLMChatModel(Builder builder) {
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
    public ChatResponse doChat(ChatRequest chatRequest) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

        try {
            GenerationResult result = modelResponse(chatRequest, null);
            String rawResponse = result.text();
            log.debug("Raw JitLLM response: {}", rawResponse);

            // Tool calls come from the engine. It reports them only when a valid call was
            // extracted and generation ended through the format's tool-call termination path, so
            // tool-shaped text that did not parse arrives here as ordinary text -- which is what it
            // is.
            List<ChatContent.ToolCall> toolCalls = result.toolCalls();
            log.debug("Extracted {} tool call(s)", toolCalls.size());
            if (!toolCalls.isEmpty()) {
                List<ToolExecutionRequest> toolExecutionRequests = JitLLMConversions.toToolExecutionRequests(toolCalls);
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(toolExecutionRequests)
                                .build())
                        .finishReason(JitLLMConversions.toLangChain4jFinishReason(result.finishReason()))
                        .build();
            }

            // Parse thinking and actual response using the JitLLMResponseParser
            JitLLMResponseParser.ParsedResponse parsed = JitLLMResponseParser.parseResponse(rawResponse);

            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text(parsed.getActualResponse())
                            .thinking(parsed.getThinkingContent())
                            .build())
                    .finishReason(JitLLMConversions.toLangChain4jFinishReason(result.finishReason()))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate response from JitLLM", e);
        }
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

        /** Public so subclasses in other packages can extend it. */
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
        public JitLLMChatModel build() {
            return new JitLLMChatModel(this);
        }
    }
}
