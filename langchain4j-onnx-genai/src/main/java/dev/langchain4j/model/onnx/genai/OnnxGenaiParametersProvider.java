package dev.langchain4j.model.onnx.genai;

/**
 * Provider interface for ONNX GenAI generation parameters.
 */
public interface OnnxGenaiParametersProvider {

    /**
     * Gets the maximum number of tokens to generate.
     *
     * @return The maximum tokens, or null for model default
     */
    Integer maxTokens();

    /**
     * Gets the temperature for sampling.
     *
     * @return The temperature value, or null for model default
     */
    Float temperature();

    /**
     * Gets the top-p parameter for nucleus sampling.
     *
     * @return The top-p value, or null for model default
     */
    Float topP();

    /**
     * Gets the top-k parameter for sampling.
     *
     * @return The top-k value, or null for model default
     */
    Integer topK();

    /**
     * Gets the repetition penalty.
     *
     * @return The repetition penalty value, or null for model default
     */
    Float repetitionPenalty();

    /**
     * Gets whether to use sampling.
     *
     * @return True to use sampling, false for greedy decoding, or null for model default
     */
    Boolean doSample();
}
