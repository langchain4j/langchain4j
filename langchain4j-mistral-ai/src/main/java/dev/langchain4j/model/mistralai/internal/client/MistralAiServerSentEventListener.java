package dev.langchain4j.model.mistralai.internal.client;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.fromJson;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolCall;
import dev.langchain4j.model.mistralai.internal.api.MistralAiUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

class MistralAiServerSentEventListener implements ServerSentEventListener {
    final StringBuffer contentBuilder;
    private final StreamingResponseHandler<AiMessage> handler;
    List<ToolExecutionRequest> toolExecutionRequests;
    TokenUsage tokenUsage;
    FinishReason finishReason;

    public MistralAiServerSentEventListener(StreamingResponseHandler<AiMessage> handler) {
        this.handler = handler;
        contentBuilder = new StringBuffer();
    }

    @Override
    public void onEvent(ServerSentEvent event) {
        String data = event.data();
        if ("[DONE]".equals(data)) {
            AiMessage aiMessage;
            if (!isNullOrEmpty(toolExecutionRequests)) {
                aiMessage = AiMessage.from(toolExecutionRequests);
            } else {
                aiMessage = AiMessage.from(contentBuilder.toString());
            }

            Response<AiMessage> response = Response.from(aiMessage, tokenUsage, finishReason);
            handler.onComplete(response);
        } else {
            try {
                MistralAiChatCompletionResponse chatCompletionResponse =
                        fromJson(data, MistralAiChatCompletionResponse.class);
                MistralAiChatCompletionChoice choice =
                        chatCompletionResponse.getChoices().get(0);

                String chunk = choice.getDelta().getContent();
                if (isNotNullOrEmpty(chunk)) {
                    contentBuilder.append(chunk);
                    handler.onNext(chunk);
                }

                List<MistralAiToolCall> toolCalls = choice.getDelta().getToolCalls();
                if (!isNullOrEmpty(toolCalls)) {
                    toolExecutionRequests = toToolExecutionRequests(toolCalls);
                }

                MistralAiUsage usageInfo = chatCompletionResponse.getUsage();
                if (usageInfo != null) {
                    this.tokenUsage = tokenUsageFrom(usageInfo);
                }

                String finishReasonString = choice.getFinishReason();
                if (finishReasonString != null) {
                    this.finishReason = finishReasonFrom(finishReasonString);
                }
            } catch (RuntimeException e) {
                handler.onError(e);
                throw e;
            } catch (Exception e) {
                handler.onError(e);
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        handler.onError(t);
    }
}
