package dev.langchain4j.model.onnx.genai;

import ai.onnxruntime.genai.GenAIException;
import ai.onnxruntime.genai.GeneratorParams;
import ai.onnxruntime.genai.SimpleGenAI;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link ChatModel} that uses ONNX Runtime GenAI for inference.
 * This implementation uses the SimpleGenAI class from the ONNX Runtime GenAI library.
 */
public class OnnxGenaiChatModel implements ChatModel, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OnnxGenaiChatModel.class);

    private final SimpleGenAI simpleGenAI;
    private final OnnxGenaiParametersProvider parametersProvider;
    private final OnnxGenaiPromptTemplate promptTemplate;

    /**
     * Creates a new instance of {@link OnnxGenaiChatModel}.
     *
     * @param simpleGenAI       The SimpleGenAI instance for generating text
     * @param parametersProvider The provider for generation parameters
     * @param promptTemplate     The prompt template for formatting chat messages
     */
    public OnnxGenaiChatModel(
            SimpleGenAI simpleGenAI,
            OnnxGenaiParametersProvider parametersProvider,
            OnnxGenaiPromptTemplate promptTemplate) {
        this.simpleGenAI = Objects.requireNonNull(simpleGenAI, "simpleGenAI cannot be null");
        this.parametersProvider = Objects.requireNonNull(parametersProvider, "parametersProvider cannot be null");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate cannot be null");
    }

    /**
     * Creates a new OnnxGenaiChatModel with default parameters.
     *
     * @param modelPath Path to the ONNX model folder
     * @return A new instance of {@link OnnxGenaiChatModel}
     */
    public static OnnxGenaiChatModel withDefaultParameters(String modelPath) {
        try {
            SimpleGenAI simpleGenAI = new SimpleGenAI(modelPath);
            return new OnnxGenaiChatModel(
                    simpleGenAI, OnnxGenaiParameters.builder().build(), OnnxGenaiPromptTemplate.defaultTemplate());
        } catch (GenAIException e) {
            throw new OnnxGenaiException("Failed to create SimpleGenAI instance", e);
        }
    }

    /**
     * Creates a new builder for {@link OnnxGenaiChatModel}.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        try (GeneratorParams params = simpleGenAI.createGeneratorParams()) {
            String prompt = promptTemplate.format(chatRequest.messages());
            applyParameters(params, parametersProvider);

            AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());
            Consumer<String> tokenListener = token -> responseBuilder.get().append(token);

            String response = simpleGenAI.generate(params, prompt, tokenListener);

            return ChatResponse.builder().aiMessage(AiMessage.from(response)).build();
        } catch (GenAIException e) {
            throw new OnnxGenaiException("Error during chat generation", e);
        }
    }

    /**
     * Apply parameters from the provider to the GeneratorParams object.
     *
     * @param params The GeneratorParams to modify
     * @param provider The parameters provider
     */
    private void applyParameters(GeneratorParams params, OnnxGenaiParametersProvider provider) {
        try {
            if (provider.maxTokens() != null) {
                params.setSearchOption("max_length", provider.maxTokens());
            }

            if (provider.temperature() != null) {
                params.setSearchOption("temperature", provider.temperature());
            }

            if (provider.topP() != null) {
                params.setSearchOption("top_p", provider.topP());
            }

            if (provider.topK() != null) {
                params.setSearchOption("top_k", provider.topK());
            }

            if (provider.repetitionPenalty() != null) {
                params.setSearchOption("repetition_penalty", provider.repetitionPenalty());
            }

            if (provider.doSample() != null) {
                params.setSearchOption("do_sample", provider.doSample());
            }
        } catch (GenAIException e) {
            logger.warn("Failed to apply some generation parameters", e);
        }
    }

    /**
     * Builder for {@link OnnxGenaiChatModel}.
     */
    public static class Builder {
        private String modelPath;
        private OnnxGenaiParameters parameters = OnnxGenaiParameters.builder().build();
        private OnnxGenaiPromptTemplate promptTemplate = OnnxGenaiPromptTemplate.defaultTemplate();

        /**
         * Sets the path to the ONNX model folder.
         *
         * @param modelPath The path to the model folder
         * @return This builder
         */
        public Builder modelPath(String modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        /**
         * Sets the generation parameters.
         *
         * @param parameters The parameters
         * @return This builder
         */
        public Builder parameters(OnnxGenaiParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        /**
         * Sets the prompt template.
         *
         * @param promptTemplate The prompt template
         * @return This builder
         */
        public Builder promptTemplate(OnnxGenaiPromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        /**
         * Builds a new instance of {@link OnnxGenaiChatModel}.
         *
         * @return A new instance
         */
        public OnnxGenaiChatModel build() {
            Objects.requireNonNull(modelPath, "modelPath must be provided");

            try {
                SimpleGenAI simpleGenAI = new SimpleGenAI(modelPath);
                return new OnnxGenaiChatModel(simpleGenAI, parameters, promptTemplate);
            } catch (GenAIException e) {
                throw new OnnxGenaiException("Failed to create SimpleGenAI instance", e);
            }
        }
    }

    @Override
    public void close() {
        simpleGenAI.close();
    }
}
