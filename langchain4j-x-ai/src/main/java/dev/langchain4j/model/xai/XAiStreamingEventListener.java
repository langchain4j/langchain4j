package dev.langchain4j.model.xai;

import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onUnmappedRawEvent;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.xai.XAiClient.CODEC;
import static dev.langchain4j.model.xai.XAiClient.chatResponseFrom;
import static dev.langchain4j.model.xai.XAiClient.integer;
import static dev.langchain4j.model.xai.XAiClient.map;
import static dev.langchain4j.model.xai.XAiClient.string;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.CancellationUnsupportedHandle;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.MappingTrackingStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps the server-sent events of a streamed xAI Responses API call onto a {@link StreamingChatResponseHandler}.
 * Text, reasoning and tool call arguments are forwarded as they arrive;
 * the complete response is built from the final {@code response.completed} event.
 */
class XAiStreamingEventListener implements ServerSentEventListener {

    private static final String STREAM_DONE_MARKER = "[DONE]";

    private static final String EVENT_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    private static final String EVENT_REASONING_TEXT_DELTA = "response.reasoning_text.delta";
    private static final String EVENT_REASONING_SUMMARY_TEXT_DELTA = "response.reasoning_summary_text.delta";
    private static final String EVENT_OUTPUT_ITEM_ADDED = "response.output_item.added";
    private static final String EVENT_FUNCTION_CALL_ARGUMENTS_DELTA = "response.function_call_arguments.delta";
    private static final String EVENT_OUTPUT_ITEM_DONE = "response.output_item.done";
    private static final String EVENT_RESPONSE_COMPLETED = "response.completed";
    private static final String EVENT_RESPONSE_INCOMPLETE = "response.incomplete";
    private static final String EVENT_RESPONSE_FAILED = "response.failed";
    private static final String EVENT_ERROR = "error";

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_DELTA = "delta";
    private static final String FIELD_ITEM = "item";
    private static final String FIELD_ITEM_ID = "item_id";
    private static final String FIELD_OUTPUT_INDEX = "output_index";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CALL_ID = "call_id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_RESPONSE = "response";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_MESSAGE = "message";

    private static final String TYPE_FUNCTION_CALL = "function_call";

    private final MappingTrackingStreamingChatResponseHandler handler;
    private final Map<String, PendingToolCall> pendingToolCalls = new HashMap<>();
    private volatile StreamingHandle streamingHandle;
    private SuccessfulHttpResponse rawHttpResponse;

    private record PendingToolCall(int index, String id, String name) {}

    XAiStreamingEventListener(StreamingChatResponseHandler handler) {
        this.handler = new MappingTrackingStreamingChatResponseHandler(handler);
    }

    @Override
    public void onOpen(SuccessfulHttpResponse response) {
        this.rawHttpResponse = response;
    }

    @Override
    public void onEvent(ServerSentEvent event) {
        onEvent(event, new ServerSentEventContext(new CancellationUnsupportedHandle()));
    }

    @Override
    public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
        if (streamingHandle == null) {
            streamingHandle = toStreamingHandle(context.parsingHandle());
        }
        if (isCancelled()) {
            return;
        }

        String data = event.data();
        if (data == null || data.isBlank() || STREAM_DONE_MARKER.equals(data.trim())) {
            return;
        }

        handler.resetMappingTracking();
        handle(data);
        if (!handler.wasMapped()) {
            onUnmappedRawEvent(handler, event);
        }
    }

    @Override
    public void onError(Throwable error) {
        withLoggingExceptions(() -> handler.onError(ExceptionMapper.DEFAULT.mapException(error)));
    }

    @SuppressWarnings("unchecked")
    private void handle(String data) {
        Map<String, Object> event = CODEC.fromJson(data, Map.class);
        String type = getOrDefault(string(event.get(FIELD_TYPE)), "");

        switch (type) {
            case EVENT_OUTPUT_TEXT_DELTA -> onPartialResponse(handler, string(event.get(FIELD_DELTA)), streamingHandle);
            case EVENT_REASONING_TEXT_DELTA, EVENT_REASONING_SUMMARY_TEXT_DELTA ->
                onPartialThinking(handler, string(event.get(FIELD_DELTA)), streamingHandle);
            case EVENT_OUTPUT_ITEM_ADDED -> onOutputItemAdded(event);
            case EVENT_FUNCTION_CALL_ARGUMENTS_DELTA -> onFunctionCallArgumentsDelta(event);
            case EVENT_OUTPUT_ITEM_DONE -> onOutputItemDone(event);
            case EVENT_RESPONSE_COMPLETED, EVENT_RESPONSE_INCOMPLETE -> onResponseCompleted(event);
            case EVENT_RESPONSE_FAILED, EVENT_ERROR -> onFailure(event);
            default -> {
                // not mapped: surfaced through onUnmappedRawEvent()
            }
        }
    }

    private void onOutputItemAdded(Map<String, Object> event) {
        Map<String, Object> item = map(event.get(FIELD_ITEM));
        if (TYPE_FUNCTION_CALL.equals(string(item.get(FIELD_TYPE)))) {
            pendingToolCalls.put(
                    string(item.get(FIELD_ID)),
                    new PendingToolCall(
                            getOrDefault(integer(event.get(FIELD_OUTPUT_INDEX)), pendingToolCalls.size()),
                            string(item.get(FIELD_CALL_ID)),
                            string(item.get(FIELD_NAME))));
        }
    }

    private void onFunctionCallArgumentsDelta(Map<String, Object> event) {
        PendingToolCall toolCall = pendingToolCalls.get(string(event.get(FIELD_ITEM_ID)));
        String delta = string(event.get(FIELD_DELTA));
        if (toolCall == null || delta == null || delta.isEmpty()) {
            return;
        }
        onPartialToolCall(
                handler,
                PartialToolCall.builder()
                        .index(toolCall.index())
                        .id(toolCall.id())
                        .name(toolCall.name())
                        .partialArguments(delta)
                        .build(),
                streamingHandle);
    }

    private void onOutputItemDone(Map<String, Object> event) {
        Map<String, Object> item = map(event.get(FIELD_ITEM));
        if (!TYPE_FUNCTION_CALL.equals(string(item.get(FIELD_TYPE)))) {
            return;
        }
        PendingToolCall pending = pendingToolCalls.remove(string(item.get(FIELD_ID)));
        int index = pending != null ? pending.index() : getOrDefault(integer(event.get(FIELD_OUTPUT_INDEX)), 0);
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id(getOrDefault(string(item.get(FIELD_CALL_ID)), string(item.get(FIELD_ID))))
                .name(string(item.get(FIELD_NAME)))
                .arguments(getOrDefault(string(item.get(FIELD_ARGUMENTS)), "{}"))
                .build();
        onCompleteToolCall(handler, new CompleteToolCall(index, toolExecutionRequest));
    }

    private void onResponseCompleted(Map<String, Object> event) {
        if (isCancelled()) {
            return;
        }
        onCompleteResponse(handler, chatResponseFrom(map(event.get(FIELD_RESPONSE)), rawHttpResponse));
    }

    private void onFailure(Map<String, Object> event) {
        Object error = event.get(FIELD_ERROR);
        if (error == null) {
            error = map(event.get(FIELD_RESPONSE)).get(FIELD_ERROR);
        }
        String message = getOrDefault(string(map(error).get(FIELD_MESSAGE)), string(event.get(FIELD_MESSAGE)));
        String errorMessage = "xAI response failed: " + (message != null ? message : CODEC.toJson(event));
        withLoggingExceptions(() -> handler.onError(new RuntimeException(errorMessage)));
    }

    private boolean isCancelled() {
        return streamingHandle != null && streamingHandle.isCancelled();
    }
}
