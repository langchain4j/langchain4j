package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSystemPrompt;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicToolChoice;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicTools;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMetadata;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.ArrayList;
import java.util.List;

@Internal
class InternalAnthropicHelper {

    private InternalAnthropicHelper() {}

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
            throw new UnsupportedFeatureException(
                    String.join(", ", unsupportedFeatures) + " are not supported by Anthropic");
        }
    }

    static AnthropicCreateMessageRequest createAnthropicRequest(
            ChatRequest chatRequest,
            AnthropicThinking thinking,
            boolean sendThinking,
            AnthropicCacheType cacheType,
            AnthropicCacheType toolsCacheType,
            boolean stream,
            String userId) {

        AnthropicCreateMessageRequest.Builder requestBuilder = AnthropicCreateMessageRequest.builder().stream(stream)
                .model(chatRequest.modelName())
                .messages(toAnthropicMessages(chatRequest.messages(), sendThinking))
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

        if (!isNullOrEmpty(userId)) {
            requestBuilder.metadata(AnthropicMetadata.builder().userId(userId).build());
        }

        return requestBuilder.build();
    }
}
