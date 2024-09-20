package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.anthropic.internal.api.*;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;

class AnthropicStreamingResponseBuilder {

    private final ReentrantLock lock = new ReentrantLock();
    final List<String> contents = synchronizedList(new ArrayList<>());
    volatile StringBuffer currentContentBuilder = new StringBuffer();
    private AnthropicContentBlockType currentContentBlockStartType;

    private final AtomicInteger inputTokenCount = new AtomicInteger();
    private final AtomicInteger outputTokenCount = new AtomicInteger();
    private final Map<Integer, AnthropicToolExecutionRequestBuilder> toolExecutionRequestBuilderMap = new HashMap<>();
    AtomicReference<String> responseId = new AtomicReference<>();
    AtomicReference<String> responseModel = new AtomicReference<>();

    volatile String stopReason;

    private StringBuffer currentContentBuilder() {
        lock.lock();
        try {
            return currentContentBuilder;
        } finally {
            lock.unlock();
        }
    }

    private void setCurrentContentBuilder(StringBuffer stringBuffer) {
        lock.lock();
        try {
            currentContentBuilder = stringBuffer;
        } finally {
            lock.unlock();
        }
    }

    public void handleMessageStart(AnthropicStreamingData data) {
        AnthropicResponseMessage message = data.message;
        if (message != null) {
            if (message.usage != null) {
                handleUsage(message.usage);
            }
            if (message.id != null) {
                responseId.set(message.id);
            }
            if (message.model != null) {
                responseModel.set(message.model);
            }
        }
    }

    private void handleUsage(AnthropicUsage usage) {
        if (usage.inputTokens != null) {
            this.inputTokenCount.addAndGet(usage.inputTokens);
        }
        if (usage.outputTokens != null) {
            this.outputTokenCount.addAndGet(usage.outputTokens);
        }
    }

    public void handleContentBlockStart(AnthropicStreamingData data) {
        if (data.contentBlock == null) {
            return;
        }

        currentContentBlockStartType = data.contentBlock.type;

        if (currentContentBlockStartType == AnthropicContentBlockType.TEXT) {
            String text = data.contentBlock.text;
            if (isNotNullOrEmpty(text)) {
                currentContentBuilder().append(text);
            }
        } else if (currentContentBlockStartType == AnthropicContentBlockType.TOOL_USE) {
            toolExecutionRequestBuilderMap.putIfAbsent(
                    data.index,
                    new AnthropicToolExecutionRequestBuilder(data.contentBlock.id, data.contentBlock.name)
            );
        }
    }

    public void handleContentBlockDelta(AnthropicStreamingData data) {
        if (data.delta == null) {
            return;
        }

        if (currentContentBlockStartType == AnthropicContentBlockType.TEXT) {
            String text = data.delta.text;
            if (isNotNullOrEmpty(text)) {
                currentContentBuilder().append(text);
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

    public void handleContentBlockStop() {
        contents.add(currentContentBuilder().toString());
        setCurrentContentBuilder(new StringBuffer());
    }

    public void handleMessageDelta(AnthropicStreamingData data) {
        if (data.delta != null) {
            AnthropicDelta delta = data.delta;
            if (delta.stopReason != null) {
                stopReason = delta.stopReason;
            }
        }
        if (data.usage != null) {
            handleUsage(data.usage);
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
                    toFinishReason(stopReason),
                    createMetadata()
            );
        }

        String content = String.join("\n", contents);
        if (!content.isEmpty()) {
            return Response.from(
                    AiMessage.from(content),
                    new TokenUsage(inputTokenCount.get(), outputTokenCount.get()),
                    toFinishReason(stopReason),
                    createMetadata()
            );
        }

        return null;
    }

    private Map<String, Object> createMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        if (responseId.get() != null) {
            metadata.put("id", responseId.get());
        }
        if (responseModel.get() != null) {
            metadata.put("model", responseModel.get());
        }
        return metadata;
    }
}
