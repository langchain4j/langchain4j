package dev.langchain4j.model.openai.internal;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.model.openai.internal.audio.OpenAiAudioTranscriptionRequest;
import dev.langchain4j.model.openai.internal.audio.OpenAiAudioTranscriptionResponse;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionResponse;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingResponse;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.moderation.ModerationRequest;
import dev.langchain4j.model.openai.internal.moderation.ModerationResponse;
import java.util.HashMap;
import java.util.Map;

public class DefaultOpenAiClient extends OpenAiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
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
        if (builder.customHeaders != null) {
            defaultHeaders.putAll(builder.customHeaders);
        }
        this.defaultHeaders = defaultHeaders;
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

    @Override
    public SyncOrAsyncOrStreaming<CompletionResponse> completion(CompletionRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .body(Json.toJson(
                        CompletionRequest.builder().from(request).stream(false).build()))
                .build();

        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
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
                .addHeaders(defaultHeaders)
                .body(Json.toJson(ChatCompletionRequest.builder().from(request).stream(false)
                        .build()))
                .build();

        HttpRequest streamingHttpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "chat/completions")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .body(Json.toJson(ChatCompletionRequest.builder().from(request).stream(true)
                        .build()))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, streamingHttpRequest, ChatCompletionResponse.class);
    }

    @Override
    public SyncOrAsync<EmbeddingResponse> embedding(EmbeddingRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "embeddings")
                .addQueryParams(customQueryParams)
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
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
                .addHeaders(defaultHeaders)
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
                .addHeaders(defaultHeaders)
                .body(Json.toJson(request))
                .build();

        return new RequestExecutor<>(httpClient, httpRequest, GenerateImagesResponse.class);
    }

    @Override
    public SyncOrAsync<OpenAiAudioTranscriptionResponse> audioTranscription(OpenAiAudioTranscriptionRequest request) {
        byte[] audioData = getBinaryDataFromAudio(request.file());

        var httpRequestBuilder = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "audio/transcriptions")
                .addHeader("Content-Type", "multipart/form-data; boundary=----langChain4j")
                .addHeaders(defaultHeaders);

        httpRequestBuilder.addFormData("model", request.model());
        httpRequestBuilder.addFile(
                "file",
                "audio" + getAudioExtension(request.file().mimeType()),
                request.file().mimeType(),
                audioData);

        if (request.language() != null) {
            httpRequestBuilder.addFormData("language", request.language());
        }

        if (request.prompt() != null) {
            httpRequestBuilder.addFormData("prompt", request.prompt());
        }

        if (request.temperature() != null) {
            httpRequestBuilder.addFormData("temperature", Double.toString(request.temperature()));
        }

        return new RequestExecutor<>(httpClient, httpRequestBuilder.build(), OpenAiAudioTranscriptionResponse.class);
    }

    private String getAudioExtension(String mimeType) {
        if (mimeType == null) return "";

        return switch (mimeType) {
            case "audio/flac" -> ".flac";
            case "audio/mpeg", "audio/mpeg3" -> ".mp3";
            case "audio/mp4", "video/mp4" -> ".mp4";
            case "audio/mpga" -> ".mpga";
            case "audio/m4a" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            case "audio/x-wav", "audio/wave", "audio/wav" -> ".wav";
            case "audio/webm", "video/webm" -> ".webm";
            case "audio/x-mpegurl", "audio/mpegurl" -> ".m3u";
            default -> ""; // Unknown; return empty or throw
        };
    }

    private byte[] getBinaryDataFromAudio(Audio audio) {
        if (audio.binaryData() != null) {
            return audio.binaryData();
        }

        if (audio.base64Data() != null) {
            try {
                return java.util.Base64.getDecoder().decode(audio.base64Data());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid base64 audio data provided", e);
            }
        }

        if (audio.url() != null) {
            throw new IllegalArgumentException(
                    "URL-based audio is not supported by OpenAI transcription. Please provide audio as binary data or base64 encoded data.");
        }

        throw new IllegalArgumentException("No audio data found. Audio must contain either binary data, base64 data");
    }
}
