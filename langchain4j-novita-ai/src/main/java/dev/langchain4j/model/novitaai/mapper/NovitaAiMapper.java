package dev.langchain4j.model.novitaai.mapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.novitaai.client.NovitaAiChatCompletionRequest.Message;
import dev.langchain4j.model.novitaai.client.NovitaAiChatCompletionResponse;
import dev.langchain4j.model.novitaai.client.NovitaAiUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.request.json.JsonSchemaElementHelper.toMap;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.toList;

@Slf4j(topic = "NovitaAiMapper")
public class NovitaAiMapper {

    public static TokenUsage tokenUsageFrom(NovitaAiUsage NovitaAiUsage) {
        if (NovitaAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                NovitaAiUsage.getPromptTokens(),
                NovitaAiUsage.getCompletionTokens(),
                NovitaAiUsage.getTotalTokens()
        );
    }

    public static FinishReason finishReasonFrom(String NovitaAiFinishReason) {
        if (NovitaAiFinishReason == null) {
            return null;
        }
        switch (NovitaAiFinishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "tool_calls":
                return TOOL_EXECUTION;
            case "content_filter":
                return CONTENT_FILTER;
            case "model_length":
            default:
                return null;
        }
    }

    public static AiMessage aiMessageFrom(NovitaAiChatCompletionResponse response) {
        Message aiNovitaMessage = response.getChoices().get(0).getMessage();
        return AiMessage.from(aiNovitaMessage.getContent());
    }

    public static RuntimeException toException(retrofit2.Response<?> retrofitResponse) throws IOException {
        int code = retrofitResponse.code();
        if (code >= 400) {
            ResponseBody errorBody = retrofitResponse.errorBody();
            if (errorBody != null) {
                String errorBodyString = errorBody.string();
                String errorMessage = String.format("status code: %s; body: %s", code, errorBodyString);
                log.error("Error response: {}", errorMessage);
                return new RuntimeException(errorMessage);
            }
        }
        return new RuntimeException(retrofitResponse.message());
    }


}
