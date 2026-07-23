package dev.langchain4j.model.onnx.genai;

import ai.onnxruntime.genai.GenAIException;
import ai.onnxruntime.genai.GeneratorParams;
import ai.onnxruntime.genai.SimpleGenAI;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link LanguageModel} that uses ONNX Runtime GenAI for inference.
 * This implementation uses the SimpleGenAI class from the ONNX Runtime GenAI library.
 */
public class OnnxGenaiLanguageModel implements LanguageModel, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OnnxGenaiLanguageModel.class);

    private final SimpleGenAI simpleGenAI;
    private final OnnxGenaiParametersProvider parametersProvider;

    /**
     * Creates a new instance of {@link OnnxGenaiLanguageModel}.
     *
     * @param simpleGenAI       The SimpleGenAI instance for generating text
     * @param parametersProvider The provider for generation parameters
     */
    public OnnxGenaiLanguageModel(SimpleGenAI simpleGenAI, OnnxGenaiParametersProvider parametersProvider) {
        this.simpleGenAI = Objects.requireNonNull(simpleGenAI, "simpleGenAI cannot be null");
        this.parametersProvider = Objects.requireNonNull(parametersProvider, "parametersProvider cannot be null");
    }

    /**
     * Creates a new OnnxGenaiLanguageModel with default parameters.
     *
     * @param modelPath Path to the ONNX model folder
     * @return A new instance of {@link OnnxGenaiLanguageModel}
     */
    public static OnnxGenaiLanguageModel withDefaultParameters(String modelPath) {
        try {
            SimpleGenAI simpleGenAI = new SimpleGenAI(modelPath);
            return new OnnxGenaiLanguageModel(
                    simpleGenAI, OnnxGenaiParameters.builder().build());
        } catch (GenAIException e) {
            throw new OnnxGenaiException("Failed to create SimpleGenAI instance", e);
        }
    }

    /**
     * Creates a new builder for {@link OnnxGenaiLanguageModel}.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Response<String> generate(String prompt) {
        try (GeneratorParams params = simpleGenAI.createGeneratorParams()) {
            applyParameters(params, parametersProvider);

            AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());
            Consumer<String> tokenListener = token -> responseBuilder.get().append(token);

            String response = simpleGenAI.generate(params, prompt, tokenListener);

            return Response.from(response);
        } catch (GenAIException e) {
            throw new OnnxGenaiException("Error during text generation", e);
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

    @Override
    public void close() {

        simpleGenAI.close();
    }

    /**
     * Builder for {@link OnnxGenaiLanguageModel}.
     */
    public static class Builder {
        private String modelPath;
        private OnnxGenaiParameters parameters = OnnxGenaiParameters.builder().build();

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
         * Builds a new instance of {@link OnnxGenaiLanguageModel}.
         *
         * @return A new instance
         */
        public OnnxGenaiLanguageModel build() {
            Objects.requireNonNull(modelPath, "modelPath must be provided");

            try {
                SimpleGenAI simpleGenAI = new SimpleGenAI(modelPath);
                return new OnnxGenaiLanguageModel(simpleGenAI, parameters);
            } catch (GenAIException e) {
                throw new OnnxGenaiException("Failed to create SimpleGenAI instance", e);
            }
        }
    }
}
