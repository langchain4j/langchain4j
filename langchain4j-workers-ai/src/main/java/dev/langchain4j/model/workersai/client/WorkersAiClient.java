package dev.langchain4j.model.workersai.client;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.workersai.client.WorkersAiJsonUtils.fromJson;
import static dev.langchain4j.model.workersai.client.WorkersAiJsonUtils.toJson;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.time.Duration;

/**
 * Low level client to interact with the WorkerAI API.
 */
public class WorkersAiClient {

    private static final String BASE_URL = "https://api.cloudflare.com/";

    private final HttpClient httpClient;
    private final String authorizationHeader;

    WorkersAiClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        // The 30s timeouts preserve the original behavior: slow, but can be needed for images.
        this.httpClient = httpClientBuilder
                .connectTimeout(getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), ofSeconds(30)))
                .readTimeout(getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), ofSeconds(30)))
                .build();

        this.authorizationHeader = "Bearer " + builder.apiToken;
    }

    /**
     * Generate chat.
     *
     * @param apiRequest request.
     * @param accountIdentifier account identifier.
     * @param modelName model name.
     * @return response.
     */
    public WorkersAiChatCompletionResponse generateChat(
            WorkersAiChatCompletionRequest apiRequest, String accountIdentifier, String modelName) {
        return checkSuccess(
                fromJson(execute(apiRequest, accountIdentifier, modelName).body(), WorkersAiChatCompletionResponse.class));
    }

    /**
     * Generate text.
     *
     * @param apiRequest request.
     * @param accountIdentifier account identifier.
     * @param modelName model name.
     * @return response.
     */
    public WorkersAiTextCompletionResponse generateText(
            WorkersAiTextCompletionRequest apiRequest, String accountIdentifier, String modelName) {
        return checkSuccess(
                fromJson(execute(apiRequest, accountIdentifier, modelName).body(), WorkersAiTextCompletionResponse.class));
    }

    /**
     * Generate image. The endpoint returns the raw binary image, so the response bytes are returned as-is.
     *
     * @param apiRequest request.
     * @param accountIdentifier account identifier.
     * @param modelName model name.
     * @return the raw image bytes.
     */
    public byte[] generateImage(
            WorkersAiImageGenerationRequest apiRequest, String accountIdentifier, String modelName) {
        return execute(apiRequest, accountIdentifier, modelName).bodyBytes();
    }

    /**
     * Generate embeddings.
     *
     * @param apiRequest request.
     * @param accountIdentifier account identifier.
     * @param modelName model name.
     * @return response.
     */
    public WorkersAiEmbeddingResponse embed(
            WorkersAiEmbeddingRequest apiRequest, String accountIdentifier, String modelName) {
        return checkSuccess(
                fromJson(execute(apiRequest, accountIdentifier, modelName).body(), WorkersAiEmbeddingResponse.class));
    }

    /**
     * Surfaces Cloudflare API errors returned in a 2xx envelope with {@code success=false}.
     * Non-2xx responses are already turned into an {@link dev.langchain4j.exception.HttpException} by the HTTP client.
     */
    private static <T extends ApiResponse<?>> T checkSuccess(T response) {
        if (response == null || !response.isSuccess()) {
            StringBuilder errorMessage = new StringBuilder("Failed to generate chat message:");
            if (response != null && response.getErrors() != null) {
                errorMessage.append(response.getErrors().stream()
                        .map(ApiResponse.Error::getMessage)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse(""));
            }
            throw new RuntimeException(errorMessage.toString());
        }
        return response;
    }

    private SuccessfulHttpResponse execute(Object apiRequest, String accountIdentifier, String modelName) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(BASE_URL + "client/v4/accounts/" + accountIdentifier + "/ai/run/" + modelName)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authorizationHeader)
                .body(toJson(apiRequest))
                .build();
        return httpClient.execute(httpRequest);
    }

    /**
     * Builder access.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link WorkersAiClient}.
     */
    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private Duration timeout;
        private String apiToken;

        /**
         * Sets the {@link HttpClientBuilder} used to create the underlying HTTP client.
         *
         * @param httpClientBuilder the HTTP client builder.
         * @return {@code this}.
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the timeout used for both connecting and reading.
         *
         * @param timeout the timeout.
         * @return {@code this}.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the API token used for authorization.
         *
         * @param apiToken the API token.
         * @return {@code this}.
         */
        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        /**
         * Builds a new {@link WorkersAiClient}.
         *
         * @return a new client instance.
         */
        public WorkersAiClient build() {
            return new WorkersAiClient(this);
        }
    }
}
