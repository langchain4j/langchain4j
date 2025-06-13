package dev.langchain4j.model.mistralai.internal.client;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionChoice;
import dev.langchain4j.model.mistralai.internal.api.MistralAiChatCompletionResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiToolCall;
import dev.langchain4j.model.mistralai.internal.api.MistralAiUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.function.BiFunction;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.mistralai.internal.client.MistralAiJsonUtils.fromJson;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.finishReasonFrom;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.toToolExecutionRequests;
import static dev.langchain4j.model.mistralai.internal.mapper.MistralAiMapper.tokenUsageFrom;

class MistralAiFimServerSentEventListener implements ServerSentEventListener {

    private final StringBuffer contentBuilder;
    private final StreamingResponseHandler<String> handler;
    private final BiFunction<String, List<ToolExecutionRequest>, String> toResponse;

    private List<ToolExecutionRequest> toolExecutionRequests;
    private TokenUsage tokenUsage;
    private FinishReason finishReason;

    public MistralAiFimServerSentEventListener(
            StreamingResponseHandler<String> handler, BiFunction<String, List<ToolExecutionRequest>, String> toResponse) {
        this.contentBuilder = new StringBuffer();
        this.handler = handler;
        this.toResponse = toResponse;
    }

    @Override
    public void onEvent(ServerSentEvent event) {
        String data = event.data();
        if ("[DONE]".equals(data)) {
            String responseContent = toResponse.apply(contentBuilder.toString(), toolExecutionRequests);
            Response<String> response = Response.from(responseContent, tokenUsage, finishReason);
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
            } catch (Exception e) {
                handler.onError(e);
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        handler.onError(t);
    }
}
