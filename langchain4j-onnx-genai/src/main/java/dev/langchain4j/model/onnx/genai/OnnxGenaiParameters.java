package dev.langchain4j.model.onnx.genai;

import ai.onnxruntime.genai.GeneratorParams;
import java.lang.reflect.Constructor;
import java.util.Objects;

/**
 * Parameters for ONNX GenAI text generation.
 */
public class OnnxGenaiParameters implements OnnxGenaiParametersProvider {

    private final Integer maxTokens;
    private final Float temperature;
    private final Float topP;
    private final Integer topK;
    private final Float repetitionPenalty;
    private final Boolean doSample;

    /**
     * Creates a new OnnxGenaiParameters with the specified values.
     *
     * @param maxTokens          Maximum tokens to generate (null for model default)
     * @param temperature        Temperature for sampling (null for model default)
     * @param topP               Top-p sampling parameter (null for model default)
     * @param topK               Top-k sampling parameter (null for model default)
     * @param repetitionPenalty  Penalty for token repetition (null for model default)
     * @param doSample           Whether to use sampling (null for model default)
     */
    private OnnxGenaiParameters(
            Integer maxTokens, Float temperature, Float topP, Integer topK, Float repetitionPenalty, Boolean doSample) {
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.repetitionPenalty = repetitionPenalty;
        this.doSample = doSample;
    }

    /**
     * Creates a new builder for OnnxGenaiParameters.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Integer maxTokens() {
        return maxTokens;
    }

    @Override
    public Float temperature() {
        return temperature;
    }

    @Override
    public Float topP() {
        return topP;
    }

    @Override
    public Integer topK() {
        return topK;
    }

    @Override
    public Float repetitionPenalty() {
        return repetitionPenalty;
    }

    @Override
    public Boolean doSample() {
        return doSample;
    }

    /**
     * Converts parameters from an OnnxGenaiParametersProvider to a GeneratorParams.
     *
     * @param provider The parameters provider
     * @return A new GeneratorParams with the parameters from the provider
     */
    public static GeneratorParams toGeneratorParams(OnnxGenaiParametersProvider provider) {
        Objects.requireNonNull(provider, "provider cannot be null");

        try {
            // Try to create GeneratorParams using reflection to handle API differences
            Constructor<GeneratorParams> constructor = getGeneratorParamsConstructor();
            GeneratorParams params = constructor.newInstance();

            if (provider.maxTokens() != null) {
                setField(params, "maxTokens", provider.maxTokens());
            }

            if (provider.temperature() != null) {
                setField(params, "temperature", provider.temperature());
            }

            if (provider.topP() != null) {
                setField(params, "topP", provider.topP());
            }

            if (provider.topK() != null) {
                setField(params, "topK", provider.topK());
            }

            if (provider.repetitionPenalty() != null) {
                setField(params, "repeatPenalty", provider.repetitionPenalty());
            }

            if (provider.doSample() != null && !provider.doSample()) {
                setField(params, "temperature", 0.0f);
            }

            return params;
        } catch (Exception e) {
            // If reflection fails, use default values
            logger().warn("Failed to set parameters directly, using default implementation", e);
            return createDefaultParams();
        }
    }

    /**
     * Get the constructor for GeneratorParams
     */
    @SuppressWarnings("unchecked")
    private static Constructor<GeneratorParams> getGeneratorParamsConstructor() throws Exception {
        try {
            // Try no-arg constructor first
            return (Constructor<GeneratorParams>) GeneratorParams.class.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            // Try to find a constructor that might work
            Constructor<?>[] constructors = GeneratorParams.class.getDeclaredConstructors();
            if (constructors.length > 0) {
                // Try to find a no-arg constructor
                for (Constructor<?> constructor : constructors) {
                    if (constructor.getParameterCount() == 0) {
                        constructor.setAccessible(true);
                        return (Constructor<GeneratorParams>) constructor;
                    }
                }

                // If no no-arg constructor, use the first one and try to provide default values
                Constructor<?> firstConstructor = constructors[0];
                firstConstructor.setAccessible(true);
                return (Constructor<GeneratorParams>) firstConstructor;
            }
            throw new OnnxGenaiException("No suitable constructor found for GeneratorParams", e);
        }
    }

    /**
     * Creates default parameters
     */
    private static GeneratorParams createDefaultParams() {
        try {
            // Try to use createDefaultParams if it exists
            java.lang.reflect.Method method = GeneratorParams.class.getDeclaredMethod("createDefaultParams");
            method.setAccessible(true);
            return (GeneratorParams) method.invoke(null);
        } catch (Exception e) {
            throw new OnnxGenaiException("Failed to create default GeneratorParams", e);
        }
    }

    /**
     * Set a field in the GeneratorParams object using reflection
     */
    private static void setField(GeneratorParams params, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = GeneratorParams.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(params, value);
        } catch (Exception e) {
            // If the field doesn't exist with this name, try alternative names
            try {
                if ("maxTokens".equals(fieldName)) {
                    setField(params, "max_length", value);
                } else if ("repeatPenalty".equals(fieldName)) {
                    setField(params, "repetition_penalty", value);
                } else if ("topK".equals(fieldName)) {
                    setField(params, "top_k", value);
                } else if ("topP".equals(fieldName)) {
                    setField(params, "top_p", value);
                }
            } catch (Exception ex) {
                logger().warn("Could not set field {} on GeneratorParams", fieldName, ex);
            }
        }
    }

    /**
     * Get a logger for this class
     */
    private static org.slf4j.Logger logger() {
        return org.slf4j.LoggerFactory.getLogger(OnnxGenaiParameters.class);
    }

    /**
     * Builder for OnnxGenaiParameters.
     */
    public static class Builder {
        private Integer maxTokens = 100;
        private Float temperature = 0.7f;
        private Float topP = 0.9f;
        private Integer topK = 50;
        private Float repetitionPenalty = 1.0f;
        private Boolean doSample = true;

        /**
         * Sets the maximum number of tokens to generate.
         *
         * @param maxTokens The maximum number of tokens
         * @return This builder
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the temperature for sampling.
         *
         * @param temperature The temperature value
         * @return This builder
         */
        public Builder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the top-p parameter for nucleus sampling.
         *
         * @param topP The top-p value
         * @return This builder
         */
        public Builder topP(Float topP) {
            this.topP = topP;
            return this;
        }

        /**
         * Sets the top-k parameter for sampling.
         *
         * @param topK The top-k value
         * @return This builder
         */
        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        /**
         * Sets the repetition penalty.
         *
         * @param repetitionPenalty The repetition penalty value
         * @return This builder
         */
        public Builder repetitionPenalty(Float repetitionPenalty) {
            this.repetitionPenalty = repetitionPenalty;
            return this;
        }

        /**
         * Sets whether to use sampling.
         *
         * @param doSample True to use sampling, false for greedy decoding
         * @return This builder
         */
        public Builder doSample(Boolean doSample) {
            this.doSample = doSample;
            return this;
        }

        /**
         * Builds a new OnnxGenaiParameters with the configured values.
         *
         * @return A new OnnxGenaiParameters instance
         */
        public OnnxGenaiParameters build() {
            return new OnnxGenaiParameters(maxTokens, temperature, topP, topK, repetitionPenalty, doSample);
        }
    }
}
