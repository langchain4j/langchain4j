package dev.langchain4j.model.openai.internal;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.StreamingHttpEvent;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.openai.OpenAiStreamingResponseBuilder;
import dev.langchain4j.model.openai.internal.audio.transcription.AudioFile;
import dev.langchain4j.model.openai.internal.audio.transcription.OpenAiAudioTranscriptionRequest;
import dev.langchain4j.model.openai.internal.audio.transcription.OpenAiAudioTranscriptionResponse;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.chat.ToolCall;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.models.ModelsListResponse;
import dev.langchain4j.model.openai.internal.moderation.ModerationRequest;
import dev.langchain4j.model.openai.internal.moderation.ModerationResponse;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.Tube;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import java.util.function.Supplier;

public class DefaultOpenAiClient extends OpenAiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
    private final Supplier<Map<String, String>> customHeadersSupplier;
    private final Map<String, String> customQueryParams;

    public DefaultOpenAiClient(Builder builder) {

        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(
                        getOrDefault(builder.connectTimeout, httpClientBuilder.connectTimeout()), ofSeconds(15)))
                .readTimeout(
                        getOrDefault(getOrDefault(builder.readTimeout, httpClientBuilder.readTimeout()), ofSeconds(60)))
                .build();

        if (builder.logRequests || builder.logResponses) {
            this.httpClient =
                    new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");

        Map<String, String> defaultHeaders = new HashMap<>();
        if (builder.apiKey != null) {
            defaultHeaders.put("Authorization", "Bearer " + builder.apiKey);
        }
        if (builder.organizationId != null) {
            defaultHeaders.put("OpenAI-Organization", builder.organizationId);
        }
        if (builder.projectId != null) {
            defaultHeaders.put("OpenAI-Project", builder.projectId);
        }
        if (builder.userAgent != null) {
            defaultHeaders.put("User-Agent", builder.userAgent);
        }
        this.defaultHeaders = defaultHeaders;
        this.customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, () -> Map::of);
        this.customQueryParams = builder.customQueryParams;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends OpenAiClient.Builder<DefaultOpenAiClient, Builder> {

        public DefaultOpenAiClient build() {
            return new DefaultOpenAiClient(this);
        }
    }

    private Map<String, String> buildRequestHeaders() {
        Map<String, String> dynamicHeaders = customHeadersSupplier.get();
        if (isNullOrEmpty(dynamicHeaders)) {
            return defaultHeaders;
        }

        Map<String, String> headers = new HashMap<>(defaultHeaders);
        headers.putAll(dynamicHeaders);
        return headers;
    }

    @Override
    public SyncOrAsyncOrStreaming<CompletionResponse> completion(CompletionRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(
                        CompletionRequest.builder().from(request).stream(false).build()))
                .build();

        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(
                        CompletionRequest.builder().from(request).stream(true).build()))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, streamingHttpRequest, CompletionResponse.class);
    }

    @Override
    public SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(ChatCompletionRequest.builder().from(request).stream(false)
                        .build()))
                .build();

        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(ChatCompletionRequest.builder().from(request).stream(true)
                        .build()))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, streamingHttpRequest, ChatCompletionResponse.class);
    }

    @Override
    public Publisher<StreamingEvent> chatCompletionPublisher(
            ChatCompletionRequest request, boolean returnThinking, boolean accumulateToolCallId) { // TODO wrap into options

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(request))
                .build();

        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER) // TODO configurable
                .withBufferSize(256); // TODO configurable

        return ZeroPublisher.create(config, tube -> {
            Publisher<StreamingHttpEvent> upstream = httpClient.executeWithPublisher(httpRequest);
            upstream.subscribe(new ChatCompletionEventSubscriber(tube, returnThinking, accumulateToolCallId));
        });
    }

    private static final class ChatCompletionEventSubscriber implements Subscriber<StreamingHttpEvent> {

        private static final String DONE_MARKER = "[DONE]";

        private final Tube<StreamingEvent> tube;
        private final boolean returnThinking;
        private final ToolCallBuilder toolCallBuilder = new ToolCallBuilder();
        private final OpenAiStreamingResponseBuilder responseBuilder;
        private SuccessfulHttpResponse rawHttpResponse;

        ChatCompletionEventSubscriber(Tube<StreamingEvent> tube, boolean returnThinking, boolean accumulateToolCallId) {
            this.tube = tube;
            this.returnThinking = returnThinking;
            this.responseBuilder = new OpenAiStreamingResponseBuilder(returnThinking, accumulateToolCallId);
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            if (tube.cancelled()) {
                subscription.cancel();
                return;
            }
            tube.whenCancelled(subscription::cancel);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamingHttpEvent item) {
            if (tube.cancelled()) {
                return;
            }
            if (item instanceof SuccessfulHttpResponse httpResponse) {
                this.rawHttpResponse = httpResponse;
                return;
            }
            if (!(item instanceof ServerSentEvent sse)) {
                return;
            }
            if (DONE_MARKER.equals(sse.data())) {
                return;
            }
            try {
                ChatCompletionResponse parsed = Json.fromJson(sse.data(), ChatCompletionResponse.class);
                @SuppressWarnings({"unchecked", "rawtypes"})
                ParsedAndRawResponse<ChatCompletionResponse> parsedAndRaw = (ParsedAndRawResponse<ChatCompletionResponse>)
                        (ParsedAndRawResponse) ParsedAndRawResponse.builder()
                                .parsedResponse(parsed)
                                .rawHttpResponse(rawHttpResponse)
                                .rawServerSentEvent(sse)
                                .build();
                responseBuilder.append(parsedAndRaw);
                emitEvents(parsed);
            } catch (Exception e) {
                tube.fail(e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!tube.cancelled()) {
                tube.fail(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (tube.cancelled()) {
                return;
            }
            if (toolCallBuilder.hasRequests()) {
                tube.send(toolCallBuilder.buildAndReset());
            }
            ChatResponse completeResponse = responseBuilder.build();
            tube.send(completeResponse);
            tube.complete();
        }

        private void emitEvents(ChatCompletionResponse response) {
            if (response == null) {
                return;
            }
            List<ChatCompletionChoice> choices = response.choices();
            if (isNullOrEmpty(choices)) {
                return;
            }
            ChatCompletionChoice choice = choices.get(0);
            if (choice == null) {
                return;
            }
            Delta delta = choice.delta();
            if (delta == null) {
                return;
            }

            String content = delta.content();
            if (isNotNullOrEmpty(content)) {
                tube.send(new PartialResponse(content));
            }

            String reasoningContent = delta.reasoningContent();
            if (returnThinking && isNotNullOrEmpty(reasoningContent)) {
                tube.send(new PartialThinking(reasoningContent));
            }

            List<ToolCall> toolCalls = delta.toolCalls();
            if (toolCalls == null) {
                return;
            }
            for (ToolCall toolCall : toolCalls) {
                int index = toolCall.index();
                if (toolCallBuilder.index() != index) {
                    tube.send(toolCallBuilder.buildAndReset());
                    toolCallBuilder.updateIndex(index);
                }
                String id = toolCallBuilder.updateId(toolCall.id());
                String name = toolCallBuilder.updateName(toolCall.function().name());
                String partialArguments = toolCall.function().arguments();
                if (isNotNullOrEmpty(partialArguments)) {
                    toolCallBuilder.appendArguments(partialArguments);
                    tube.send(PartialToolCall.builder()
                            .index(index)
                            .id(id)
                            .name(name)
                            .partialArguments(partialArguments)
                            .build());
                }
            }
        }
    }

    @Override
    public SyncOrAsync<EmbeddingResponse> embedding(EmbeddingRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "embeddings")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(request))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, EmbeddingResponse.class);
    }

    @Override
    public SyncOrAsync<ModerationResponse> moderation(ModerationRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "moderations")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(request))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, ModerationResponse.class);
    }

    @Override
    public SyncOrAsync<GenerateImagesResponse> imagesGeneration(GenerateImagesRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "images/generations")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(request))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, GenerateImagesResponse.class);
    }

    @Override
    public SyncOrAsync<OpenAiAudioTranscriptionResponse> audioTranscription(OpenAiAudioTranscriptionRequest request) {
        HttpRequest.Builder httpRequestBuilder = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "audio/transcriptions")
                .addHeader("Content-Type", "multipart/form-data; boundary=----LangChain4j")
                .addHeaders(buildRequestHeaders());

        httpRequestBuilder.addFormDataField("model", request.model());

        AudioFile file = request.file();
        httpRequestBuilder.addFormDataFile("file", file.fileName(), file.mimeType(), file.content());

        if (request.language() != null) {
            httpRequestBuilder.addFormDataField("language", request.language());
        }

        if (request.prompt() != null) {
            httpRequestBuilder.addFormDataField("prompt", request.prompt());
        }

        if (request.temperature() != null) {
            httpRequestBuilder.addFormDataField("temperature", Double.toString(request.temperature()));
        }

        return new RequestExecutor<>(httpClient, httpRequestBuilder.build(), OpenAiAudioTranscriptionResponse.class);
    }

    @Override
    public SyncOrAsync<ModelsListResponse> listModels() {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(GET)
                .url(baseUrl, "models")
                .addQueryParams(customQueryParams)
                .addHeaders(buildRequestHeaders())
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, ModelsListResponse.class);
    }
}
