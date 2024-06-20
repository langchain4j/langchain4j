package dev.langchain4j.model.anthropic.internal.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.*;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.internal.api.*;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.http.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toFinishReason;
import static java.util.Collections.synchronizedList;

public class DefaultAnthropicClient extends AnthropicClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private final HttpClient httpClient;
    private final String baseUrl;

    private final String apiKey;
    private final String version;
    private final String beta;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AnthropicClient.Builder<DefaultAnthropicClient, Builder> {

        public DefaultAnthropicClient build() {
            return new DefaultAnthropicClient(this);
        }
    }

    DefaultAnthropicClient(Builder builder) {
        if (isNullOrBlank(builder.apiKey)) {
            throw new IllegalArgumentException("Anthropic API key must be defined. " +
                    "It can be generated here: https://console.anthropic.com/settings/keys");
        }

        this.httpClient = ensureNotNull(builder.httpClientBuilder, "httpClientBuilder")
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                // TODO other fields
                .build();
        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");

        this.apiKey = builder.apiKey;
        this.version = ensureNotBlank(builder.version, "version");
        this.beta = builder.beta;
    }

    @Override
    public AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request) {
        try {
            HttpRequest httpRequest = toHttpRequest(request);
            HttpResponse httpResponse = httpClient.execute(httpRequest);
            return OBJECT_MAPPER.readValue(httpResponse.body(), AnthropicCreateMessageResponse.class);
        } catch (IOException e) {
            throw new RuntimeException(e); // TODO
        }
    }

    private String toBeta(AnthropicCreateMessageRequest request) {
        return hasTools(request) ? beta : null;
    }

    private static boolean hasTools(AnthropicCreateMessageRequest request) {
        return !isNullOrEmpty(request.tools) || request.messages.stream()
                .flatMap(message -> message.content.stream())
                .anyMatch(content ->
                        (content instanceof AnthropicToolUseContent) || (content instanceof AnthropicToolResultContent));
    }

    @Override
    public void createMessage(AnthropicCreateMessageRequest request, StreamingResponseHandler<AiMessage> handler) {

        try {
            HttpRequest httpRequest = toHttpRequest(request);

            httpClient.execute(httpRequest, new ServerSentEventListener() {

                final List<String> contents = synchronizedList(new ArrayList<>());
                volatile StringBuffer currentContentBuilder = new StringBuffer();

                final AtomicInteger inputTokenCount = new AtomicInteger();
                final AtomicInteger outputTokenCount = new AtomicInteger();

                volatile String stopReason;

                private synchronized StringBuffer currentContentBuilder() {
                    return currentContentBuilder;
                }

                private synchronized void setCurrentContentBuilder(StringBuffer stringBuffer) {
                    currentContentBuilder = stringBuffer;
                }

                @Override
                public void onEvent(ServerSentEvent event) {
                    try {
                        String dataString = event.getData();
                        AnthropicStreamingData data = OBJECT_MAPPER.readValue(dataString, AnthropicStreamingData.class);

                        String type = event.getType();
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

                private void handleMessageStart(AnthropicStreamingData data) {
                    if (data.message != null && data.message.usage != null) {
                        handleUsage(data.message.usage);
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

                private void handleContentBlockStart(AnthropicStreamingData data) {
                    if (data.contentBlock != null && "text".equals(data.contentBlock.type)) {
                        String text = data.contentBlock.text;
                        if (isNotNullOrEmpty(text)) {
                            currentContentBuilder().append(text);
                            handler.onNext(text);
                        }
                    }
                }

                private void handleContentBlockDelta(AnthropicStreamingData data) {
                    if (data.delta != null && "text_delta".equals(data.delta.type)) {
                        String text = data.delta.text;
                        if (isNotNullOrEmpty(text)) {
                            currentContentBuilder().append(text);
                            handler.onNext(text);
                        }
                    }
                }

                private void handleContentBlockStop() {
                    contents.add(currentContentBuilder().toString());
                    setCurrentContentBuilder(new StringBuffer());
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
                    Response<AiMessage> response = Response.from(
                            AiMessage.from(String.join("\n", contents)),
                            new TokenUsage(inputTokenCount.get(), outputTokenCount.get()),
                            toFinishReason(stopReason)
                    );
                    handler.onComplete(response);
                }

                private void handleError(String dataString) {
                    handler.onError(new AnthropicHttpException(null, dataString)); // TODO
                }

                @Override
                public void onError(Throwable throwable) {
                    handler.onError(throwable); // TODO
                }
            });
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    private HttpRequest toHttpRequest(AnthropicCreateMessageRequest request) throws JsonProcessingException {
        return HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "/messages")
                .addHeader("content-type", "application/json") // TODO set by default?
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", version)
                .addHeader("anthropic-beta", toBeta(request))
                .body(OBJECT_MAPPER.writeValueAsString(request))
                .build();
    }
}
