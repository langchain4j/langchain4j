package dev.langchain4j.model.mistralai;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionRequest;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiMessages;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiResponseFormat;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiToolChoiceName;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toMistralAiTools;

@Internal
class InternalMistralAIHelper {

    private InternalMistralAIHelper() { }

    static void validate(ChatRequestParameters parameters) {
        List<String> unsupportedFeatures = new ArrayList<>();
        if (parameters.topK() != null) {
            unsupportedFeatures.add("topK");
        }

        if (!unsupportedFeatures.isEmpty()) {
            if (unsupportedFeatures.size() == 1) {
                throw new UnsupportedFeatureException(unsupportedFeatures.get(0) + " is not supported by Mistral AI");
            }
            throw new UnsupportedFeatureException(String.join(", ", unsupportedFeatures) + " are not supported by Mistral AI");
        }
    }

    static MistralAiChatCompletionRequest createMistralAiRequest(ChatRequest chatRequest, Boolean safePrompt, Integer randomSeed, boolean stream) {
        MistralAiChatCompletionRequest.MistralAiChatCompletionRequestBuilder requestBuilder =
                MistralAiChatCompletionRequest.builder()
                        .model(chatRequest.modelName())
                        .messages(toMistralAiMessages(chatRequest.messages()))
                        .temperature(chatRequest.temperature())
                        .maxTokens(chatRequest.maxOutputTokens())
                        .topP(chatRequest.topP())
                        .randomSeed(randomSeed)
                        .safePrompt(safePrompt)
                        .responseFormat(toMistralAiResponseFormat(chatRequest.responseFormat()))
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

        return requestBuilder.build();
    }
}
