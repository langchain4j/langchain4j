package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

public class OllamaModels {

    private final OllamaClient client;
    private final Integer maxRetries;

    public OllamaModels(OllamaModelsBuilder builder) {
        this.client = OllamaClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    public static OllamaModelsBuilder builder() {
        return new OllamaModelsBuilder();
    }

    public Response<List<OllamaModel>> availableModels() {
        ModelsListResponse response = withRetryMappingExceptions(client::listModels, maxRetries);
        return Response.from(response.getModels());
    }

    public Response<OllamaModelCard> modelCard(OllamaModel ollamaModel) {
        return modelCard(ollamaModel.getName());
    }

    public Response<OllamaModelCard> modelCard(String modelName) {
        OllamaModelCard response = withRetryMappingExceptions(
                () -> client.showInformation(
                        ShowModelInformationRequest.builder().name(modelName).build()),
                maxRetries);
        return Response.from(response);
    }

    public void deleteModel(OllamaModel ollamaModel) {
        deleteModel(ollamaModel.getName());
    }

    public void deleteModel(String ollamaModelName) {
        withRetryMappingExceptions(
                () -> client.deleteModel(
                        DeleteModelRequest.builder().name(ollamaModelName).build()),
                maxRetries);
    }

    public Response<List<RunningOllamaModel>> runningModels() {
        RunningModelsListResponse response = withRetryMappingExceptions(client::listRunningModels, maxRetries);
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
         * Sets the {@link HttpClientBuilder} that will be used to create the {@link HttpClient}
         * that will be used to communicate with Ollama.
         * <p>
         * NOTE: {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}.
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
            return new OllamaModels(this);
        }
    }
}
