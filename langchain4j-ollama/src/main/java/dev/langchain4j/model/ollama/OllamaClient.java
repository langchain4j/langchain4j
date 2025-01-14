package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.HttpClient;
import dev.langchain4j.http.HttpClientBuilder;
import dev.langchain4j.http.HttpMethod;
import dev.langchain4j.http.HttpRequest;
import dev.langchain4j.http.HttpResponse;
import dev.langchain4j.http.ServerSentEvent;
import dev.langchain4j.http.ServerSentEventListener;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.http.HttpMethod.DELETE;
import static dev.langchain4j.http.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.createModelListenerRequest;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenError;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenRequest;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenResponse;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.getObjectMapper;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.toObject;
import static java.lang.Boolean.TRUE;

class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final HttpClient httpClient;
    private final String baseUrl; // TODO
    private final boolean logStreamingResponses;

    OllamaClient(Builder builder) {

        this.httpClient = builder.httpClientBuilder
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .build();

        this.baseUrl = Utils.ensureTrailingForwardSlash(builder.baseUrl);

        // TODO
//        if (logRequests != null && logRequests) {
//            okHttpClientBuilder.addInterceptor(new OllamaRequestLoggingInterceptor());
//        }
//        if (logResponses != null && logResponses) {
//            okHttpClientBuilder.addInterceptor(new OllamaResponseLoggingInterceptor());
//        }
        this.logStreamingResponses = getOrDefault(builder.logStreamingResponses, false);

        // TODO
        // add custom header interceptor
//        if (customHeaders != null && !customHeaders.isEmpty()) {
//            okHttpClientBuilder.addInterceptor(new GenericHeadersInterceptor(customHeaders));
//        }
    }

    static Builder builder() {
        return new Builder();
    }

    public CompletionResponse completion(CompletionRequest request) {

        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/generate")
                    .addHeader("Content-Type", "application/json") // TODO always set by default?
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            HttpResponse httpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(httpResponse.body(), CompletionResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public ChatResponse chat(ChatRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/chat")
                    .addHeader("Content-Type", "application/json") // TODO always set by default?
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            HttpResponse httpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(httpResponse.body(), ChatResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void streamingCompletion(CompletionRequest request, StreamingResponseHandler<String> handler) {

        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/generate")
                    .addHeader("Content-Type", "application/x-ndjson")
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            httpClient.execute(httpRequest, new ServerSentEventListener() {

                final StringBuilder contentBuilder = new StringBuilder();

                @Override
                public void onEvent(ServerSentEvent event) {

                    String partialResponse = event.data();

                    if (logStreamingResponses) {
                        log.debug("Streaming partial response: {}", partialResponse);
                    }

                    CompletionResponse completionResponse = toObject(partialResponse, CompletionResponse.class);
                    contentBuilder.append(completionResponse.getResponse());
                    handler.onNext(completionResponse.getResponse());

                    if (TRUE.equals(completionResponse.getDone())) {
                        Response<String> response = Response.from(
                                contentBuilder.toString(),
                                new TokenUsage(
                                        completionResponse.getPromptEvalCount(),
                                        completionResponse.getEvalCount()
                                )
                        );
                        handler.onComplete(response);
                    }
                }

                @Override
                public void onFinish() {
//                    handler.onComplete(); TODO?
                }

                @Override
                public void onError(Throwable throwable) {
                    handler.onError(throwable);
                }
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e); // TODO
        }
    }

    public void streamingChat(ChatRequest request, StreamingResponseHandler<AiMessage> handler,
                              List<ChatModelListener> listeners, List<ChatMessage> messages) {

        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, new ArrayList<>());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        onListenRequest(listeners, modelListenerRequest, attributes);

        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/chat")
                    .addHeader("Content-Type", "application/x-ndjson")
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            httpClient.execute(httpRequest, new ServerSentEventListener() {

                final OllamaStreamingResponseBuilder responseBuilder = new OllamaStreamingResponseBuilder();

                @Override
                public void onEvent(ServerSentEvent event) {

                    String partialResponse = event.data();

                    if (logStreamingResponses) {
                        log.debug("Streaming partial response: {}", partialResponse);
                    }

                    ChatResponse chatResponse = toObject(partialResponse, ChatResponse.class);
                    String content = chatResponse.getMessage().getContent();
                    responseBuilder.append(chatResponse);
                    handler.onNext(content);

                    if (TRUE.equals(chatResponse.getDone())) {
                        Response<AiMessage> response = responseBuilder.build();
                        handler.onComplete(response);

                        onListenResponse(listeners, response, modelListenerRequest, attributes); // TODO before or after?
                    }
                }

                @Override
                public void onFinish() {
//                    handler.onComplete(); TODO?
                }

                @Override
                public void onError(Throwable throwable) {
                    handler.onError(throwable);
                    // TODO call onListenError here?
                    onListenError(listeners, throwable, modelListenerRequest, responseBuilder.build(), attributes);
                }
            });
        } catch (JsonProcessingException e) {
            // TODO onListenError(...)?
            throw new RuntimeException(e); // TODO
        }
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/embed")
                    .addHeader("Content-Type", "application/json") // TODO always set by default?
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            HttpResponse httpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(httpResponse.body(), EmbeddingResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public ModelsListResponse listModels() {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(HttpMethod.GET) // TODO
                    .url(baseUrl, "api/tags")
                    .addHeader("Content-Type", "application/json") // TODO always set by default?
                    .build();

            HttpResponse httpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(httpResponse.body(), ModelsListResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public OllamaModelCard showInformation(ShowModelInformationRequest showInformationRequest) {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/show")
                    .addHeader("Content-Type", "application/json") // TODO always set by default?
                    .body(getObjectMapper().writeValueAsString(showInformationRequest))
                    .build();

            HttpResponse httpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(httpResponse.body(), OllamaModelCard.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public RunningModelsListResponse listRunningModels() {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(HttpMethod.GET) // TODO
                    .url(baseUrl, "api/ps")
                    .addHeader("Content-Type", "application/json") // TODO always set by default?
                    .build();

            HttpResponse httpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(httpResponse.body(), RunningModelsListResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Void deleteModel(DeleteModelRequest deleteModelRequest) {

        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(DELETE)
                    .url(baseUrl, "api/delete")
                    .addHeader("Content-Type", "application/json") // TODO always set by default?
                    .body(getObjectMapper().writeValueAsString(deleteModelRequest))
                    .build();

            HttpResponse httpResponse = httpClient.execute(httpRequest);

            return null; // TODO
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

//    static class GenericHeadersInterceptor implements Interceptor { // TODO remove?
//
//        private final Map<String, String> headers = new HashMap<>();
//
//        GenericHeadersInterceptor(Map<String, String> headers) {
//            Optional.ofNullable(headers)
//                    .ifPresent(this.headers::putAll);
//        }
//
//        @NotNull
//        @Override
//        public okhttp3.Response intercept(Chain chain) throws IOException {
//            Request.Builder builder = chain.request().newBuilder();
//
//            // Add headers
//            this.headers.forEach(builder::addHeader);
//
//            return chain.proceed(builder.build());
//        }
//    }

    static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Boolean logStreamingResponses;
        private Map<String, String> customHeaders;

        Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        Builder logStreamingResponses(Boolean logStreamingResponses) {
            this.logStreamingResponses = logStreamingResponses;
            return this;
        }

        Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        OllamaClient build() {
            return new OllamaClient(this);
        }
    }
}
