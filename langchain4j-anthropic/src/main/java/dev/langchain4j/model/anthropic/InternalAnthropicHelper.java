package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.anthropic.internal.api.AnthropicToolChoice;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSystemPrompt;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicTools;
import static dev.langchain4j.model.anthropic.internal.sanitizer.MessageSanitizer.sanitizeMessages;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static java.util.Collections.singletonList;

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

        List<ChatMessage> sanitizedMessages = sanitizeMessages(chatRequest.messages());
        List<AnthropicTextContent> systemPrompt = toAnthropicSystemPrompt(chatRequest.messages(), cacheType);

        AnthropicCreateMessageRequest.Builder requestBuilder = AnthropicCreateMessageRequest.builder()
                .stream(stream)
                .model(chatRequest.modelName())
                .messages(toAnthropicMessages(sanitizedMessages))
                .system(systemPrompt)
                .maxTokens(chatRequest.maxOutputTokens())
                .stopSequences(chatRequest.stopSequences())
                .temperature(chatRequest.temperature())
                .topP(chatRequest.topP())
                .topK(chatRequest.topK())
                .thinking(thinking);

        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();
        if (!isNullOrEmpty(toolSpecifications)) {
            if (chatRequest.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(String.format(
                            "%s.%s is currently supported only when there is a single tool",
                            ToolChoice.class.getSimpleName(), REQUIRED.name()));
                }
                ToolSpecification toolThatMustBeExecuted = toolSpecifications.get(0);
                requestBuilder.tools(toAnthropicTools(singletonList(toolThatMustBeExecuted), toolsCacheType));
                requestBuilder.toolChoice(AnthropicToolChoice.from(toolThatMustBeExecuted.name()));
            } else {
                requestBuilder.tools(toAnthropicTools(toolSpecifications, toolsCacheType));
            }
        }

        return requestBuilder.build();
    }
}
