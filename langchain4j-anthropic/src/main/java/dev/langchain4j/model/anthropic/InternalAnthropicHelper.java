package dev.langchain4j.model.anthropic;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSystemPrompt;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicToolChoice;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicTools;

@Internal
class InternalAnthropicHelper {

    private InternalAnthropicHelper() { }

    static void validate(ChatRequestParameters parameters) {
        List<String> unsupportedFeatures = new ArrayList<>();
        if (parameters.responseFormat() != null) {
            unsupportedFeatures.add("JSON response format");
        }
        if (parameters.frequencyPenalty() != null) {
            unsupportedFeatures.add("Frequency Penalty");
        }
        if (parameters.presencePenalty() != null) {
            unsupportedFeatures.add("Presence Penalty");
        }

        if (!unsupportedFeatures.isEmpty()) {
            if (unsupportedFeatures.size() == 1) {
                throw new UnsupportedFeatureException(unsupportedFeatures.get(0) + " is not supported by Anthropic");
            }
            throw new UnsupportedFeatureException(String.join(", ", unsupportedFeatures) + " are not supported by Anthropic");
        }
    }

    static AnthropicCreateMessageRequest createAnthropicRequest(ChatRequest chatRequest,
                                                                AnthropicThinking thinking,
                                                                AnthropicCacheType cacheType,
                                                                AnthropicCacheType toolsCacheType,
                                                                boolean stream) {

        AnthropicCreateMessageRequest.Builder requestBuilder = AnthropicCreateMessageRequest.builder()
                .stream(stream)
                .model(chatRequest.modelName())
                .messages(toAnthropicMessages(chatRequest.messages()))
                .system(toAnthropicSystemPrompt(chatRequest.messages(), cacheType))
                .maxTokens(chatRequest.maxOutputTokens())
                .stopSequences(chatRequest.stopSequences())
                .temperature(chatRequest.temperature())
                .topP(chatRequest.topP())
                .topK(chatRequest.topK())
                .thinking(thinking);

        if (!isNullOrEmpty(chatRequest.toolSpecifications())) {
            requestBuilder.tools(toAnthropicTools(chatRequest.toolSpecifications(), toolsCacheType));
        }
        if (chatRequest.toolChoice() != null) {
            requestBuilder.toolChoice(toAnthropicToolChoice(chatRequest.toolChoice()));
        }

        return requestBuilder.build();
    }
}
