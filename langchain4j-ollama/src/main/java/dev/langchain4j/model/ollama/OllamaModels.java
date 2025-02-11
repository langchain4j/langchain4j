package dev.langchain4j.model.ollama;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;

public class OllamaModels {

    private final OllamaClient client;
    private final Integer maxRetries;

    public OllamaModels(HttpClientBuilder httpClientBuilder,
                        String baseUrl,
                        Duration timeout,
                        Integer maxRetries,
                        Boolean logRequests,
                        Boolean logResponses) {
        this.client = OllamaClient.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public static OllamaModelsBuilder builder() {
        return new OllamaModelsBuilder();
    }

    public Response<List<OllamaModel>> availableModels() {
        ModelsListResponse response = withRetry(client::listModels, maxRetries);
        return Response.from(response.getModels());
    }

    public Response<OllamaModelCard> modelCard(OllamaModel ollamaModel) {
        return modelCard(ollamaModel.getName());
    }

    public Response<OllamaModelCard> modelCard(String modelName) {
        OllamaModelCard response = withRetry(() -> client.showInformation(
                ShowModelInformationRequest.builder()
                        .name(modelName)
                        .build()
        ), maxRetries);
        return Response.from(response);
    }

    public void deleteModel(OllamaModel ollamaModel) {
        deleteModel(ollamaModel.getName());
    }

    public void deleteModel(String ollamaModelName) {
        withRetry(() -> client.deleteModel(
                DeleteModelRequest.builder()
                        .name(ollamaModelName)
                        .build()
        ), maxRetries);
    }

    public Response<List<RunningOllamaModel>> runningModels() {
        RunningModelsListResponse response = withRetry(client::listRunningModels, maxRetries);
        return Response.from(response.getModels());
    }

    public static class OllamaModelsBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;

        /**
         * TODO
         * TODO {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}
         *
         * @param httpClientBuilder
         * @return
         */
        public OllamaModelsBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OllamaModelsBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OllamaModelsBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OllamaModelsBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OllamaModelsBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OllamaModelsBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaModels build() {
            return new OllamaModels(httpClientBuilder, baseUrl, timeout, maxRetries, logRequests, logResponses);
        }
    }
}
