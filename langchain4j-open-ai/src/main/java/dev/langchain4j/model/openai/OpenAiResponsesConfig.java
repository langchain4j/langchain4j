package dev.langchain4j.model.openai;

import java.util.List;

class OpenAiResponsesConfig {

    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final Integer maxToolCalls;
    private final Boolean parallelToolCalls;
    private final String previousResponseId;
    private final Integer topLogprobs;
    private final String truncation;
    private final List<String> include;
    private final String serviceTier;
    private final String safetyIdentifier;
    private final String promptCacheKey;
    private final String promptCacheRetention;
    private final String reasoningEffort;
    private final String textVerbosity;
    private final Boolean streamIncludeObfuscation;
    private final Boolean store;
    private final Boolean strict;

    OpenAiResponsesConfig(
            String modelName,
            Double temperature,
            Double topP,
            Integer maxOutputTokens,
            Integer maxToolCalls,
            Boolean parallelToolCalls,
            String previousResponseId,
            Integer topLogprobs,
            String truncation,
            List<String> include,
            String serviceTier,
            String safetyIdentifier,
            String promptCacheKey,
            String promptCacheRetention,
            String reasoningEffort,
            String textVerbosity,
            Boolean streamIncludeObfuscation,
            Boolean store,
            Boolean strict) {
        this.modelName = modelName;
        this.temperature = temperature;
        this.topP = topP;
        this.maxOutputTokens = maxOutputTokens;
        this.maxToolCalls = maxToolCalls;
        this.parallelToolCalls = parallelToolCalls;
        this.previousResponseId = previousResponseId;
        this.topLogprobs = topLogprobs;
        this.truncation = truncation;
        this.include = include;
        this.serviceTier = serviceTier;
        this.safetyIdentifier = safetyIdentifier;
        this.promptCacheKey = promptCacheKey;
        this.promptCacheRetention = promptCacheRetention;
        this.reasoningEffort = reasoningEffort;
        this.textVerbosity = textVerbosity;
        this.streamIncludeObfuscation = streamIncludeObfuscation;
        this.store = store;
        this.strict = strict;
    }

    String modelName() {
        return modelName;
    }

    Double temperature() {
        return temperature;
    }

    Double topP() {
        return topP;
    }

    Integer maxOutputTokens() {
        return maxOutputTokens;
    }

    Integer maxToolCalls() {
        return maxToolCalls;
    }

    Boolean parallelToolCalls() {
        return parallelToolCalls;
    }

    String previousResponseId() {
        return previousResponseId;
    }

    Integer topLogprobs() {
        return topLogprobs;
    }

    String truncation() {
        return truncation;
    }

    List<String> include() {
        return include;
    }

    String serviceTier() {
        return serviceTier;
    }

    String safetyIdentifier() {
        return safetyIdentifier;
    }

    String promptCacheKey() {
        return promptCacheKey;
    }

    String promptCacheRetention() {
        return promptCacheRetention;
    }

    String reasoningEffort() {
        return reasoningEffort;
    }

    String textVerbosity() {
        return textVerbosity;
    }

    Boolean streamIncludeObfuscation() {
        return streamIncludeObfuscation;
    }

    Boolean store() {
        return store;
    }

    Boolean strict() {
        return strict;
    }
}
