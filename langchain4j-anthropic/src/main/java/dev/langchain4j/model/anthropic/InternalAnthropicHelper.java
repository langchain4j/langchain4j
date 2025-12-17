package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSystemPrompt;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicToolChoice;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicTools;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.chat.request.ResponseFormatType.TEXT;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMetadata;
import dev.langchain4j.model.anthropic.internal.api.AnthropicOutputFormat;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTool;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Internal
class InternalAnthropicHelper {

    private InternalAnthropicHelper() {}

    static void validate(ChatRequestParameters parameters) {
        List<String> unsupportedFeatures = new ArrayList<>();
        if (parameters.frequencyPenalty() != null) {
            unsupportedFeatures.add("Frequency Penalty");
        }
        if (parameters.presencePenalty() != null) {
            unsupportedFeatures.add("Presence Penalty");
        }
        if (parameters.responseFormat() != null && parameters.responseFormat().type() == JSON
                && parameters.responseFormat().jsonSchema() == null) {
            unsupportedFeatures.add("Schemaless JSON response format");
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
            String toolChoiceName,
            Boolean disableParallelToolUse,
            List<AnthropicServerTool> serverTools,
            Set<String> toolMetadataKeysToSend,
            String userId,
            Map<String, Object> customParameters,
            Boolean strictTools) {

        AnthropicCreateMessageRequest.Builder requestBuilder = AnthropicCreateMessageRequest.builder().stream(stream)
                .model(chatRequest.modelName())
                .messages(toAnthropicMessages(chatRequest.messages(), sendThinking))
                .system(toAnthropicSystemPrompt(chatRequest.messages(), cacheType))
                .maxTokens(chatRequest.maxOutputTokens())
                .stopSequences(chatRequest.stopSequences())
                .temperature(chatRequest.temperature())
                .topP(chatRequest.topP())
                .topK(chatRequest.topK())
                .thinking(thinking)
                .outputFormat(toAnthropicOutputFormat(chatRequest.responseFormat()))
                .customParameters(customParameters);

        List<AnthropicTool> tools = new ArrayList<>();
        if (!isNullOrEmpty(serverTools)) {
            tools.addAll(toAnthropicTools(serverTools));
        }
        if (!isNullOrEmpty(chatRequest.toolSpecifications())) {
            tools.addAll(toAnthropicTools(chatRequest.toolSpecifications(), toolsCacheType, toolMetadataKeysToSend, strictTools));
        }
        if (!tools.isEmpty()) {
            requestBuilder.tools(tools);
        }

        if (chatRequest.toolChoice() != null) {
            requestBuilder.toolChoice(
                    toAnthropicToolChoice(chatRequest.toolChoice(), toolChoiceName, disableParallelToolUse));
        }

        if (!isNullOrEmpty(userId)) {
            requestBuilder.metadata(AnthropicMetadata.builder().userId(userId).build());
        }

        return requestBuilder.build();
    }

    public static AnthropicOutputFormat toAnthropicOutputFormat(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == TEXT || responseFormat.jsonSchema() == null) {
            return null;
        }

        return AnthropicOutputFormat.fromJsonSchema(responseFormat.jsonSchema());
    }
}
