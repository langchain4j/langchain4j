package dev.langchain4j.model.anthropic.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicDelta;
import dev.langchain4j.model.anthropic.internal.api.AnthropicResponseMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicStreamingData;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TEXT;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicContentBlockType.TOOL_USE;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Internal
class AnthropicServerSentEventListener<T> implements ServerSentEventListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
    private final StreamingResponseHandler<T> handler;
    private final BiFunction<String, List<ToolExecutionRequest>, T> toResponse;

    final List<String> contents = synchronizedList(new ArrayList<>());

    final AtomicReference<AnthropicContentBlockType> currentContentBlockStartType = new AtomicReference<>();
    final Map<Integer, AnthropicToolExecutionRequestBuilder> toolExecutionRequestBuilderMap = new ConcurrentHashMap<>();

    final AtomicInteger inputTokenCount = new AtomicInteger();
    final AtomicInteger outputTokenCount = new AtomicInteger();

    final AtomicInteger cacheCreationInputTokens = new AtomicInteger();
    final AtomicInteger cacheReadInputTokens = new AtomicInteger();

    final AtomicReference<String> responseId = new AtomicReference<>();
    final AtomicReference<String> responseModel = new AtomicReference<>();

    volatile String stopReason;

    List<ToolExecutionRequest> toolExecutionRequests;
    TokenUsage tokenUsage;
    FinishReason finishReason;
    final StringBuffer contentBuilder;

    public AnthropicServerSentEventListener(
            StreamingResponseHandler<T> handler, BiFunction<String, List<ToolExecutionRequest>, T> toResponse) {
        this.handler = handler;
        this.toResponse = toResponse;
        contentBuilder = new StringBuffer();
    }

    @Override
    public void onEvent(final ServerSentEvent event) {
        String dataString = event.data();
        if ("[DONE]".equals(dataString)) {
            T responseContent = toResponse.apply(contentBuilder.toString(), toolExecutionRequests);
            Response<T> response = Response.from(responseContent, tokenUsage, finishReason);
            handler.onComplete(response);
        } else {

            try {
                AnthropicStreamingData data = OBJECT_MAPPER.readValue(dataString, AnthropicStreamingData.class);
                String type = data.type;

                if ("message_start".equals(type)) {
                    handleMessageStart(data);
                } else if ("content_block_start".equals(type)) {
                    handleContentBlockStart(data);
                } else if ("content_block_delta".equals(type)) {
                    handleContentBlockDelta(data);
                } else if ("content_block_stop".equals(type)) {
                    handleContentBlockStop();
                } else if ("message_delta".equals(type)) {
                    handleMessageDelta(data);
                } else if ("message_stop".equals(type)) {
                    handleMessageStop();
                } else if ("error".equals(type)) {
                    handleError(dataString);
                }
            } catch (Exception e) {
                handler.onError(e);
            }
        }
    }

    private void handleMessageStart(AnthropicStreamingData data) {
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

    private void handleContentBlockStart(AnthropicStreamingData data) {
        if (data.contentBlock == null) {
            return;
        }

        currentContentBlockStartType.set(data.contentBlock.type);

        if (currentContentBlockStartType.get() == TEXT) {
            String text = data.contentBlock.text;
            if (isNotNullOrEmpty(text)) {
                contentBuilder.append(text);
                handler.onNext(text);
            }
        } else if (currentContentBlockStartType.get() == TOOL_USE) {
            toolExecutionRequestBuilderMap.putIfAbsent(
                    data.index,
                    new AnthropicToolExecutionRequestBuilder(data.contentBlock.id, data.contentBlock.name)
            );
        }
    }


    private void handleContentBlockDelta(AnthropicStreamingData data) {
        if (data.delta == null) {
            return;
        }

        if (currentContentBlockStartType.get() == TEXT) {
            String text = data.delta.text;
            if (isNotNullOrEmpty(text)) {
                contentBuilder.append(text);
                handler.onNext(text);
            }
        } else if (currentContentBlockStartType.get() == TOOL_USE) {
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

    private void handleContentBlockStop() {
        contents.add(contentBuilder.toString());
    }

    private void handleMessageDelta(AnthropicStreamingData data) {
        if (data.delta != null) {
            AnthropicDelta delta = data.delta;
            if (delta.stopReason != null) {
                this.stopReason = delta.stopReason;
            }
        }
        if (data.usage != null) {
            handleUsage(data.usage);
        }
    }

    private void handleMessageStop() {
        Response<T> response = (Response<T>) build();
        handler.onComplete(response);
    }

    private void handleUsage(AnthropicUsage usage) {
        if (usage.inputTokens != null) {
            this.inputTokenCount.addAndGet(usage.inputTokens);
        }
        if (usage.outputTokens != null) {
            this.outputTokenCount.addAndGet(usage.outputTokens);
        }
        if (usage.cacheCreationInputTokens != null) {
            this.cacheCreationInputTokens.addAndGet(usage.cacheCreationInputTokens);
        }
        if (usage.cacheReadInputTokens != null) {
            this.cacheReadInputTokens.addAndGet(usage.cacheReadInputTokens);
        }
    }

    private void handleError(String dataString) {
        handler.onError(new AnthropicHttpException(null, dataString));
    }

    private Response<AiMessage> build() {

        String text = contents.stream()
                .filter(content -> !content.isEmpty())
                .collect(joining("\n"));

        AnthropicTokenUsage tokenUsage = AnthropicTokenUsage.builder()
                .inputTokenCount(inputTokenCount.get())
                .outputTokenCount(outputTokenCount.get())
                .cacheCreationInputTokens(cacheCreationInputTokens.get())
                .cacheReadInputTokens(cacheReadInputTokens.get())
                .build();

        FinishReason finishReason = toFinishReason(stopReason);

        Map<String, Object> metadata = createMetadata();

        if (toolExecutionRequestBuilderMap.isEmpty()) {
            return Response.from(
                    AiMessage.from(text),
                    tokenUsage,
                    finishReason,
                    metadata
            );
        } else {
            List<ToolExecutionRequest> toolExecutionRequests = toolExecutionRequestBuilderMap
                    .values().stream()
                    .map(AnthropicToolExecutionRequestBuilder::build)
                    .collect(toList());

            AiMessage aiMessage = isNullOrBlank(text)
                    ? AiMessage.from(toolExecutionRequests)
                    : AiMessage.from(text, toolExecutionRequests);

            return Response.from(
                    aiMessage,
                    tokenUsage,
                    finishReason,
                    metadata
            );
        }
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

    @Override
    public void onError(final Throwable throwable) {
        handler.onError(throwable);
    }
}
