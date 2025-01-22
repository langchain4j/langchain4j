package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.http.client.streaming.NdJsonStrategy;
import dev.langchain4j.http.client.streaming.ServerSentEvent;
import dev.langchain4j.http.client.streaming.ServerSentEventListener;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.http.client.HttpMethod.DELETE;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.createModelListenerRequest;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenError;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenRequest;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenResponse;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.getObjectMapper;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.toObject;
import static java.lang.Boolean.TRUE;
import static java.time.Duration.ofSeconds;

class OllamaClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;

    OllamaClient(Builder builder) {

        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), ofSeconds(10))) // TODO default value
                .readTimeout(getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), ofSeconds(60)))
                .build();

        if (builder.logRequests != null || builder.logResponses != null) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        this.defaultHeaders = copyIfNotNull(builder.customHeaders);
    }

    static Builder builder() {
        return new Builder();
    }

    public CompletionResponse completion(CompletionRequest request) {

        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/generate")
                    .addHeader("Content-Type", "application/json")
                    .addHeaders(defaultHeaders)
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(successfulHttpResponse.body(), CompletionResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public ChatResponse chat(ChatRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/chat")
                    .addHeader("Content-Type", "application/json")
                    .addHeaders(defaultHeaders)
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(successfulHttpResponse.body(), ChatResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void streamingCompletion(CompletionRequest request, StreamingResponseHandler<String> handler) {

        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/generate")
                    .addHeader("Content-Type", "application/x-ndjson") // TODO x-nd?
                    .addHeaders(defaultHeaders)
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            httpClient.execute(httpRequest, new NdJsonStrategy(), new ServerSentEventListener() {

                final StringBuilder contentBuilder = new StringBuilder();

                @Override
                public void onEvent(ServerSentEvent event) {

                    CompletionResponse completionResponse = toObject(event.data(), CompletionResponse.class);
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
                public void onClose() {
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
                    .addHeader("Content-Type", "application/x-ndjson") // TODO x-nd?
                    .addHeaders(defaultHeaders)
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            httpClient.execute(httpRequest, new NdJsonStrategy(), new ServerSentEventListener() {

                final OllamaStreamingResponseBuilder responseBuilder = new OllamaStreamingResponseBuilder();

                @Override
                public void onEvent(ServerSentEvent event) {

                    ChatResponse chatResponse = toObject(event.data(), ChatResponse.class);
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
                public void onClose() {
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
                    .addHeader("Content-Type", "application/json")
                    .addHeaders(defaultHeaders)
                    .body(getObjectMapper().writeValueAsString(request))
                    .build();

            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(successfulHttpResponse.body(), EmbeddingResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public ModelsListResponse listModels() {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(GET)
                    .url(baseUrl, "api/tags")
                    .addHeader("Content-Type", "application/json")
                    .addHeaders(defaultHeaders)
                    .build();

            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(successfulHttpResponse.body(), ModelsListResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public OllamaModelCard showInformation(ShowModelInformationRequest showInformationRequest) {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(POST)
                    .url(baseUrl, "api/show")
                    .addHeader("Content-Type", "application/json")
                    .addHeaders(defaultHeaders)
                    .body(getObjectMapper().writeValueAsString(showInformationRequest))
                    .build();

            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(successfulHttpResponse.body(), OllamaModelCard.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public RunningModelsListResponse listRunningModels() {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(GET)
                    .url(baseUrl, "api/ps")
                    .addHeader("Content-Type", "application/json")
                    .addHeaders(defaultHeaders)
                    .build();

            SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

            return getObjectMapper().readValue(successfulHttpResponse.body(), RunningModelsListResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Void deleteModel(DeleteModelRequest deleteModelRequest) {
        try {
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(DELETE)
                    .url(baseUrl, "api/delete")
                    .addHeader("Content-Type", "application/json")
                    .addHeaders(defaultHeaders)
                    .body(getObjectMapper().writeValueAsString(deleteModelRequest))
                    .build();
            httpClient.execute(httpRequest);
            return null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
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

        Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        OllamaClient build() {
            return new OllamaClient(this);
        }
    }
}
