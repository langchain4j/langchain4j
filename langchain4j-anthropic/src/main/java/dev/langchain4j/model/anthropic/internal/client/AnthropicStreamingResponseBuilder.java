package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.anthropic.internal.api.*;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;

class AnthropicStreamingResponseBuilder {

    private final AtomicInteger inputTokenCount = new AtomicInteger();
    private final AtomicInteger outputTokenCount = new AtomicInteger();
    private AnthropicContentBlockType currentContentBlockStartType;
    private final StringBuffer textBuilder = new StringBuffer();
    private final Map<Integer, AnthropicToolExecutionRequestBuilder> toolExecutionRequestBuilderMap = new HashMap<>();

    volatile String stopReason;

    public AnthropicStreamingResponseBuilder() {}

    public void messageStart(AnthropicStreamingData data) {
        if (data.message != null && data.message.usage != null) {
            updateUsage(data.message.usage);
        }
    }

    private void updateUsage(AnthropicUsage usage) {
        if (usage.inputTokens != null) {
            this.inputTokenCount.addAndGet(usage.inputTokens);
        }
        if (usage.outputTokens != null) {
            this.outputTokenCount.addAndGet(usage.outputTokens);
        }
    }

    public void contentBlockStart(AnthropicStreamingData data) {
        if (data.contentBlock == null) {
            return;
        }

        currentContentBlockStartType = data.contentBlock.type;

        if (currentContentBlockStartType == AnthropicContentBlockType.TEXT) {
            String text = data.contentBlock.text;
            if (isNotNullOrEmpty(text)) {
                textBuilder.append(text);
            }
        } else if (currentContentBlockStartType == AnthropicContentBlockType.TOOL_USE) {
            toolExecutionRequestBuilderMap.putIfAbsent(
                    data.index,
                    new AnthropicToolExecutionRequestBuilder(data.contentBlock.id, data.contentBlock.name)
            );
        }
    }

    public void contentBlockDelta(AnthropicStreamingData data) {
        if (data.delta == null) {
            return;
        }

        if (currentContentBlockStartType == AnthropicContentBlockType.TEXT) {
            String text = data.delta.text;
            if (isNotNullOrEmpty(text)) {
                textBuilder.append(text);
            }
        } else if (currentContentBlockStartType == AnthropicContentBlockType.TOOL_USE) {
            String partialJson = data.delta.partialJson;
            if (isNotNullOrEmpty(partialJson)) {
                Integer toolExecutionsIndex = data.index;
                if (toolExecutionsIndex != null) {
                    AnthropicToolExecutionRequestBuilder toolExecutionRequestBuilder = toolExecutionRequestBuilderMap.get(toolExecutionsIndex);
                    toolExecutionRequestBuilder.appendArguments(partialJson);
                }
            }
        }
    }

    public void messageDelta(AnthropicStreamingData data) {
        if (data.delta != null) {
            AnthropicDelta delta = data.delta;
            if (delta.stopReason != null) {
                stopReason = delta.stopReason;
            }
        }
        if (data.usage != null) {
            updateUsage(data.usage);
        }
    }

    public Response<AiMessage> build() {
        if (!toolExecutionRequestBuilderMap.isEmpty()) {
            List<ToolExecutionRequest> toolExecutionRequests = toolExecutionRequestBuilderMap
                    .values().stream()
                    .map(AnthropicToolExecutionRequestBuilder::build)
                    .collect(Collectors.toList());
            return Response.from(
                    AiMessage.from(toolExecutionRequests),
                    new TokenUsage(inputTokenCount.get(), outputTokenCount.get()),
                    toFinishReason(stopReason)
            );
        }

        String content = textBuilder.toString();
        if (!content.isEmpty()) {
            return Response.from(
                    AiMessage.from(textBuilder.toString()),
                    new TokenUsage(inputTokenCount.get(), outputTokenCount.get()),
                    toFinishReason(stopReason)
            );
        }

        return null;
    }
}
