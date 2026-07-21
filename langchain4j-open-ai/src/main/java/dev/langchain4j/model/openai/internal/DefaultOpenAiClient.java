package dev.langchain4j.model.openai.internal;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onUnmappedRawEvent;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.openai.internal.ChatCompletionEventDispatcher.handle;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.MappingTrackingStreamingChatResponseHandler;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.chat.response.StreamingEvent;
import dev.langchain4j.model.openai.OpenAiStreamingResponseBuilder;
import dev.langchain4j.model.openai.internal.audio.texttospeech.OpenAiTextToSpeechRequest;
import dev.langchain4j.model.openai.internal.audio.texttospeech.OpenAiTextToSpeechResponse;
import dev.langchain4j.model.openai.internal.audio.transcription.AudioFile;
import dev.langchain4j.model.openai.internal.audio.transcription.OpenAiAudioTranscriptionRequest;
import dev.langchain4j.model.openai.internal.audio.transcription.OpenAiAudioTranscriptionResponse;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.openai.internal.image.EditImageRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.image.ImageFile;
import dev.langchain4j.model.openai.internal.models.ModelsListResponse;
import dev.langchain4j.model.openai.internal.moderation.ModerationRequest;
import dev.langchain4j.model.openai.internal.moderation.ModerationResponse;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.Tube;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import java.util.function.Supplier;

public class DefaultOpenAiClient extends OpenAiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
    private final Supplier<Map<String, String>> customHeadersSupplier;
    private final Map<String, String> customQueryParams;
    private final int streamingBufferSize;

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
        this.streamingBufferSize = ensureGreaterThanZero(
                getOrDefault(builder.streamingBufferSize, DEFAULT_STREAMING_BUFFER_SIZE), "streamingBufferSize");
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
            ChatCompletionRequest request, ChatCompletionOptions options) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(request))
                .build();

        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(streamingBufferSize);

        return ZeroPublisher.create(config, tube -> {
            Publisher<HttpStreamingEvent> upstream = httpClient.stream(httpRequest);
            upstream.subscribe(new ChatCompletionEventSubscriber(tube, options));
        });
    }

    private static final class ChatCompletionEventSubscriber implements Subscriber<HttpStreamingEvent> {

        private static final String DONE_MARKER = "[DONE]";

        private final Tube<StreamingEvent> tube;
        private final TubeBackedStreamingChatResponseHandler tubeHandler;
        private final MappingTrackingStreamingChatResponseHandler handler;
        private final ChatCompletionOptions options;
        private final ToolCallBuilder toolCallBuilder = new ToolCallBuilder();
        private final OpenAiStreamingResponseBuilder responseBuilder;
        private SuccessfulHttpResponse rawHttpResponse;

        ChatCompletionEventSubscriber(Tube<StreamingEvent> tube, ChatCompletionOptions options) {
            this.tube = ensureNotNull(tube, "tube");
            this.tubeHandler = new TubeBackedStreamingChatResponseHandler(tube);
            this.handler = new MappingTrackingStreamingChatResponseHandler(tubeHandler);
            this.options = ensureNotNull(options, "options");
            this.responseBuilder = new OpenAiStreamingResponseBuilder(options.returnThinking(), options.accumulateToolCallId());
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            if (tube.cancelled()) {
                subscription.cancel();
                return;
            }
            // whenTerminates (not whenCancelled): abort the upstream HTTP stream on ANY terminal signal - downstream
            // cancel, an error, or a buffer overflow - so overflow actually aborts the connection.
            tube.whenTerminates(subscription::cancel);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(HttpStreamingEvent item) {
            if (tube.cancelled()) {
                return;
            }
            if (item instanceof HttpResponseReceived responseReceived) {
                this.rawHttpResponse = responseReceived.response();
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
                ParsedAndRawResponse<ChatCompletionResponse> parsedAndRaw = ParsedAndRawResponse.builder()
                                .parsedResponse(parsed)
                                .rawHttpResponse(rawHttpResponse)
                                .rawServerSentEvent(sse)
                                .streamingHandle(tubeHandler.streamingHandle())
                                .build();
                responseBuilder.append(parsedAndRaw);

                handler.resetMappingTracking();
                handle(parsedAndRaw, toolCallBuilder, handler, options.returnThinking());
                if (!handler.wasMapped()) {
                    onUnmappedRawEvent(handler, sse);
                }
            } catch (Exception e) {
                tube.fail(e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!tube.cancelled()) {
                tube.fail(ExceptionMapper.DEFAULT.mapException(throwable));
            }
        }

        @Override
        public void onComplete() {
            if (tube.cancelled()) {
                return;
            }
            try {
                if (toolCallBuilder.hasRequests()) {
                    handler.onCompleteToolCall(toolCallBuilder.buildAndReset());
                }
                handler.onCompleteResponse(responseBuilder.build());
            } catch (Exception e) {
                if (!tube.cancelled()) {
                    tube.fail(e);
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
    public SyncOrAsync<GenerateImagesResponse> imagesEdit(EditImageRequest request) {
        HttpRequest.Builder httpRequestBuilder = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "images/edits")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "multipart/form-data; boundary=----LangChain4j")
                .addHeaders(buildRequestHeaders());

        ImageFile image = request.image();
        httpRequestBuilder.addFormDataFile("image", image.fileName(), image.mimeType(), image.content());

        httpRequestBuilder.addFormDataField("prompt", request.prompt());

        if (request.mask() != null) {
            ImageFile mask = request.mask();
            httpRequestBuilder.addFormDataFile("mask", mask.fileName(), mask.mimeType(), mask.content());
        }

        if (request.model() != null) {
            httpRequestBuilder.addFormDataField("model", request.model());
        }

        httpRequestBuilder.addFormDataField("n", Integer.toString(request.n()));

        if (request.size() != null) {
            httpRequestBuilder.addFormDataField("size", request.size());
        }

        if (request.quality() != null) {
            httpRequestBuilder.addFormDataField("quality", request.quality());
        }

        if (request.user() != null) {
            httpRequestBuilder.addFormDataField("user", request.user());
        }

        if (request.background() != null) {
            httpRequestBuilder.addFormDataField("background", request.background());
        }

        if (request.outputFormat() != null) {
            httpRequestBuilder.addFormDataField("output_format", request.outputFormat());
        }

        if (request.outputCompression() != null) {
            httpRequestBuilder.addFormDataField("output_compression", Integer.toString(request.outputCompression()));
        }

        return new RequestExecutor<>(httpClient, httpRequestBuilder.build(), GenerateImagesResponse.class);
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
    public SyncOrAsync<OpenAiTextToSpeechResponse> textToSpeech(OpenAiTextToSpeechRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "audio/speech")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(buildRequestHeaders())
                .body(Json.toJson(request))
                .build();

        return new RequestExecutor<>(
                httpClient,
                httpRequest,
                response -> OpenAiTextToSpeechResponse.from(response.bodyBytes(), response.contentType()));
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
