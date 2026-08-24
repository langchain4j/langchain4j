package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiMessages;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiResponseFormat;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiToolChoiceName;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiTools;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;
import java.util.ArrayList;
import java.util.List;

@Internal
class InternalMistralAIHelper {

    private InternalMistralAIHelper() {}

    static void validate(ChatRequestParameters parameters) {
        List<String> unsupportedFeatures = new ArrayList<>();
        if (parameters.topK() != null) {
            unsupportedFeatures.add("topK");
        }

        if (!unsupportedFeatures.isEmpty()) {
            if (unsupportedFeatures.size() == 1) {
                throw new UnsupportedFeatureException(unsupportedFeatures.get(0) + " is not supported by Mistral AI");
            }
            throw new UnsupportedFeatureException(
                    String.join(", ", unsupportedFeatures) + " are not supported by Mistral AI");
        }
    }

    static MistralAiChatCompletionRequest createMistralAiRequest(
            ChatRequest chatRequest, boolean stream, boolean strictJsonSchema) {

        MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder requestBuilder =
                MistralAiChatCompletionRequest.builder()
                        .model(chatRequest.modelName())
                        .temperature(chatRequest.temperature())
                        .maxTokens(chatRequest.maxOutputTokens())
                        .topP(chatRequest.topP())
                        .responseFormat(toMistralAiResponseFormat(chatRequest.responseFormat(), strictJsonSchema))
                        .stop(chatRequest.stopSequences().toArray(new String[0]))
                        .frequencyPenalty(chatRequest.frequencyPenalty())
                        .presencePenalty(chatRequest.presencePenalty())
                        .stream(stream);

        if (!isNullOrEmpty(chatRequest.toolSpecifications())) {
            requestBuilder.tools(toMistralAiTools(chatRequest.toolSpecifications()));
        }
        if (chatRequest.toolChoice() != null) {
            requestBuilder.toolChoice(toMistralAiToolChoiceName(chatRequest.toolChoice()));
        }
        if (chatRequest.parameters() instanceof MistralAiChatRequestParameters parameters) {
            requestBuilder
                    .messages(
                            toMistralAiMessages(chatRequest.messages(), getOrDefault(parameters.sendThinking(), false)))
                    .randomSeed(parameters.randomSeed())
                    .safePrompt(parameters.safePrompt())
                    .promptCacheKey(parameters.promptCacheKey())
                    .reasoningEffort(parameters.reasoningEffort())
                    .serviceTier(parameters.serviceTier());
        }

        return requestBuilder.build();
    }
}
