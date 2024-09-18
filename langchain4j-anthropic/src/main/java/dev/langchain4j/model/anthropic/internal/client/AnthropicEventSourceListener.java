package dev.langchain4j.model.anthropic.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.internal.api.AnthropicStreamingData;
import dev.langchain4j.model.output.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

class AnthropicEventSourceListener extends EventSourceListener {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
    private static final Logger LOGGER = LoggerFactory.getLogger(AnthropicEventSourceListener.class);
    private final StreamingResponseHandler<AiMessage> handler;
    private final boolean logResponses;
    private final AnthropicStreamingResponseBuilder responseBuilder = new AnthropicStreamingResponseBuilder();

    public AnthropicEventSourceListener(StreamingResponseHandler<AiMessage> handler, boolean logResponses) {
        this.handler = handler;
        this.logResponses = logResponses;
    }

    @Override
    public void onOpen(EventSource eventSource, okhttp3.Response response) {
        if (logResponses) {
            LOGGER.debug("onOpen()");
        }
    }

    @Override
    public void onEvent(EventSource eventSource, String id, String type, String dataString) {
        if (logResponses) {
            LOGGER.debug("onEvent() type: '{}', data: {}", type, dataString);
        }

        try {
            AnthropicStreamingData partialResponse = OBJECT_MAPPER.readValue(dataString, AnthropicStreamingData.class);

            if ("message_start".equals(type)) {
                responseBuilder.messageStart(partialResponse);
            } else if ("content_block_start".equals(type)) {
                responseBuilder.contentBlockStart(partialResponse);
            } else if ("content_block_delta".equals(type)) {
                responseBuilder.contentBlockDelta(partialResponse);
                String text = partialResponse.delta.text;
                if (text != null && !text.isEmpty()) {
                    handler.onNext(text);
                }
                String partialJson = partialResponse.delta.partialJson;
                if (partialJson != null && !partialJson.isEmpty()) {
                    handler.onNext(partialJson);
                }
            } else if ("message_delta".equals(type)) {
                responseBuilder.messageDelta(partialResponse);
            } else if ("message_stop".equals(type)) {
                Response<AiMessage> message = responseBuilder.build();
                handler.onComplete(message);
            } else if ("error".equals(type)) {
                handler.onError(new AnthropicHttpException(null, dataString));
            }
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    @Override
    public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
        if (logResponses) {
            LOGGER.debug("onFailure()", t);
        }

        if (t != null) {
            handler.onError(t);
        }

        if (response != null) {
            try (ResponseBody responseBody = response.body()) {
                if (responseBody != null) {
                    handler.onError(new AnthropicHttpException(response.code(), responseBody.string()));
                } else {
                    handler.onError(new AnthropicHttpException(response.code(), null));
                }
            } catch (IOException e) {
                handler.onError(new AnthropicHttpException(response.code(), "[error reading response body]"));
            }
        }
    }

    @Override
    public void onClosed(EventSource eventSource) {
        if (logResponses) {
            LOGGER.debug("onClosed()");
        }
    }
}
