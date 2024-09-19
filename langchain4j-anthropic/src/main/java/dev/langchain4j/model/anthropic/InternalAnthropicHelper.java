package dev.langchain4j.model.anthropic;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.client.AnthropicHttpException;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Map;

class InternalAnthropicHelper {

    static ChatModelErrorContext createErrorContext(Throwable e,
                                                    ChatModelRequest modelListenerRequest,
                                                    Map<Object, Object> attributes) {
        Throwable error;
        if (e.getCause() instanceof AnthropicHttpException) {
            error = e.getCause();
        } else {
            error = e;
        }

        return new ChatModelErrorContext(
                error,
                modelListenerRequest,
                null,
                attributes
        );
    }

    static ChatModelRequest createModelListenerRequest(AnthropicCreateMessageRequest request,
                                                       List<ChatMessage> messages,
                                                       List<ToolSpecification> toolSpecifications) {
        return ChatModelRequest.builder()
                .model(request.getModel())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .maxTokens(request.getMaxTokens())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    static ChatModelResponse createModelListenerResponse(String responseId,
                                                         String responseModel,
                                                         Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
                .id(responseId)
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }
}
