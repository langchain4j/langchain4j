package dev.langchain4j.model.openai.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.Map;
import java.util.Set;

@Internal
public class OpenAiResponsesValidator {

    private static final Set<String> VALID_TRUNCATION_VALUES = Set.of("auto", "disabled");

    private static final Set<String> VALID_INCLUDE_VALUES = Set.of(
            "input",
            "output",
            "usage",
            "file_search_call.results",
            "web_search_call.results",
            "web_search_call.action.sources",
            "message.input_image.image_url",
            "computer_call_output.output.image_url",
            "code_interpreter_call.outputs",
            "reasoning.encrypted_content",
            "message.output_text.logprobs");

    private static final Set<String> VALID_SERVICE_TIER_VALUES = Set.of("auto", "default", "priority", "flex");

    private static final Set<String> VALID_REASONING_EFFORT_VALUES = Set.of("none", "minimal", "low", "medium", "high");

    private static final Set<String> VALID_TEXT_VERBOSITY_VALUES = Set.of("low", "medium", "high");

    private static final Map<String, Set<String>> PARAMETER_ALLOWED_VALUES = Map.of(
            "truncation", VALID_TRUNCATION_VALUES,
            "service_tier", VALID_SERVICE_TIER_VALUES,
            "reasoning.effort", VALID_REASONING_EFFORT_VALUES);

    public static void validate(ChatRequestParameters parameters) {
        if (parameters == null) {
            return;
        }

        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException("'topK' parameter is not supported by OpenAI Responses API");
        }

        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(
                    "'frequencyPenalty' parameter is not supported by OpenAI Responses API");
        }

        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException(
                    "'presencePenalty' parameter is not supported by OpenAI Responses API");
        }

        if (parameters.stopSequences() != null && !parameters.stopSequences().isEmpty()) {
            throw new UnsupportedFeatureException("'stopSequences' parameter is not supported by OpenAI Responses API");
        }

        validateMaxOutputTokens(parameters.maxOutputTokens());
    }

    public static void validateTruncation(String truncation) {
        if (truncation != null && !VALID_TRUNCATION_VALUES.contains(truncation)) {
            throw new IllegalArgumentException(
                    "Invalid truncation value: '" + truncation + "'. Allowed values: " + VALID_TRUNCATION_VALUES);
        }
    }

    public static void validateInclude(java.util.List<String> include) {
        if (include != null) {
            for (String value : include) {
                if (!VALID_INCLUDE_VALUES.contains(value)) {
                    throw new IllegalArgumentException(
                            "Invalid include value: '" + value + "'. Allowed values: " + VALID_INCLUDE_VALUES);
                }
            }
        }
    }

    public static void validateServiceTier(String serviceTier) {
        if (serviceTier != null && !VALID_SERVICE_TIER_VALUES.contains(serviceTier)) {
            throw new IllegalArgumentException(
                    "Invalid service_tier value: '" + serviceTier + "'. Allowed values: " + VALID_SERVICE_TIER_VALUES);
        }
    }

    public static void validateReasoningEffort(String reasoningEffort) {
        if (reasoningEffort != null && !VALID_REASONING_EFFORT_VALUES.contains(reasoningEffort)) {
            throw new IllegalArgumentException("Invalid reasoning effort value: '"
                    + reasoningEffort
                    + "'. Allowed values: "
                    + VALID_REASONING_EFFORT_VALUES);
        }
    }

    public static void validateTemperature(Double temperature) {
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("Temperature must be between 0.0 and 2.0, but was: " + temperature);
        }
    }

    public static void validateTopP(Double topP) {
        if (topP != null && (topP < 0.0 || topP > 1.0)) {
            throw new IllegalArgumentException("topP must be between 0.0 and 1.0, but was: " + topP);
        }
    }

    public static void validateMaxOutputTokens(Integer maxOutputTokens) {
        if (maxOutputTokens != null && maxOutputTokens < 16) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be at least 16 for Responses API, but was: " + maxOutputTokens);
        }
    }

    public static void validateTopLogprobs(Integer topLogprobs) {
        if (topLogprobs != null && (topLogprobs < 0 || topLogprobs > 20)) {
            throw new IllegalArgumentException("topLogprobs must be between 0 and 20, but was: " + topLogprobs);
        }
    }

    public static void validateTextVerbosity(String textVerbosity) {
        if (textVerbosity != null && !VALID_TEXT_VERBOSITY_VALUES.contains(textVerbosity)) {
            throw new IllegalArgumentException("Invalid text.verbosity value: '"
                    + textVerbosity
                    + "'. Allowed values: "
                    + VALID_TEXT_VERBOSITY_VALUES);
        }
    }
}
