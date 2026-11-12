package dev.langchain4j.model.vertexai.gemini;

import static dev.langchain4j.internal.CustomHeadersUtils.customHeadersOf;

import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.Objects;

class VertexAiGeminiRequestParameters {

    private VertexAiGeminiRequestParameters() {}

    static ChatRequestParameters defaultRequestParameters(
            GenerativeModel generativeModel, GenerationConfig generationConfig) {
        return ChatRequestParameters.builder()
                .modelName(generativeModel.getModelName())
                .temperature(generationConfig.hasTemperature() ? (double) generationConfig.getTemperature() : null)
                .topP(generationConfig.hasTopP() ? (double) generationConfig.getTopP() : null)
                .topK(generationConfig.hasTopK() ? (int) generationConfig.getTopK() : null)
                .maxOutputTokens(generationConfig.hasMaxOutputTokens() ? generationConfig.getMaxOutputTokens() : null)
                .build();
    }

    /**
     * Rejects the parameters this model does not support per request. Values equal to the configured ones (which come
     * in through {@link #defaultRequestParameters(GenerativeModel, GenerationConfig)}) are accepted; temperature and
     * topP are compared with the precision of the configuration ({@code float}). Per-request
     * {@link ChatRequestParameters#httpParameters()} are rejected when they contain headers to send.
     */
    static void validateParameters(ChatRequestParameters parameters, ChatRequestParameters defaults) {
        if (!customHeadersOf(parameters).isEmpty()) {
            throw new UnsupportedFeatureException(
                    "'httpParameters' parameter (per-request custom HTTP headers) is not supported by Vertex AI Gemini, "
                            + "as the Vertex AI SDK sets headers only when the model is built. "
                            + "Set them once with customHeaders(...) on the model builder, "
                            + "or use the langchain4j-google-genai module, which supports per-request headers "
                            + "(also for Vertex AI)");
        }
        ChatRequestValidationUtils.validateParameters(ChatRequestParameters.builder()
                .modelName(differing(parameters.modelName(), defaults.modelName()))
                .temperature(differingAsFloat(parameters.temperature(), defaults.temperature()))
                .topP(differingAsFloat(parameters.topP(), defaults.topP()))
                .topK(differing(parameters.topK(), defaults.topK()))
                .maxOutputTokens(differing(parameters.maxOutputTokens(), defaults.maxOutputTokens()))
                .frequencyPenalty(parameters.frequencyPenalty())
                .presencePenalty(parameters.presencePenalty())
                .stopSequences(parameters.stopSequences())
                .build());
    }

    private static <T> T differing(T value, T configured) {
        return Objects.equals(value, configured) ? null : value;
    }

    private static Double differingAsFloat(Double value, Double configured) {
        if (value != null && configured != null && value.floatValue() == configured.floatValue()) {
            return null;
        }
        return value;
    }
}
