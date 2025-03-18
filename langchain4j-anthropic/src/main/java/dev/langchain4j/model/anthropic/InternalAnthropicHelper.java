package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.client.AnthropicHttpException;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;

class InternalAnthropicHelper {

    static ChatModelErrorContext createErrorContext(
            Throwable e, ChatRequest listenerRequest, ModelProvider modelProvider, Map<Object, Object> attributes) {
        Throwable error;
        if (e.getCause() instanceof AnthropicHttpException) {
            error = e.getCause();
        } else {
            error = e;
        }

        return new ChatModelErrorContext(error, listenerRequest, modelProvider, attributes);
    }

    static ChatRequest createListenerRequest(
            AnthropicCreateMessageRequest request,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(request.getModel())
                        .temperature(request.getTemperature())
                        .topP(request.getTopP())
                        .maxOutputTokens(request.getMaxTokens())
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }

    static ChatResponse createListenerResponse(String responseId, String responseModel, Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .id(responseId)
                        .modelName(responseModel)
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }
}
