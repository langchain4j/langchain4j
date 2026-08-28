package dev.langchain4j.model.ovhai.internal.client;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.ovhai.internal.client.OvhAiJsonUtils.fromJson;
import static dev.langchain4j.model.ovhai.internal.client.OvhAiJsonUtils.toJson;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingRequest;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingResponse;
import java.util.Arrays;

/**
 * @deprecated Do not use anymore, use {@code langchain4j-open-ai} module instead
 */
@Deprecated(forRemoval = true, since = "1.14.0")
public class DefaultOvhAiClient extends OvhAiClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorizationHeader;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends OvhAiClient.Builder<DefaultOvhAiClient, Builder> {

        public DefaultOvhAiClient build() {
            return new DefaultOvhAiClient(this);
        }
    }

    DefaultOvhAiClient(Builder builder) {
        ensureNotBlank(builder.apiKey, "%s", "OVHcloud API key must be defined. It can be generated here: https://endpoints.ai.cloud.ovh.net/");

        HttpClientBuilder httpClientBuilder = HttpClientBuilderLoader.loadHttpClientBuilder();
        HttpClient httpClient = httpClientBuilder
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses) {
            this.httpClient =
                    new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureTrailingForwardSlash(ensureNotBlank(builder.baseUrl, "baseUrl"));
        this.authorizationHeader = "Bearer " + ensureNotBlank(builder.apiKey, "apiKey");
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "api/batch_text2vec")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authorizationHeader)
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);

        float[][] embeddings = fromJson(response.body(), float[][].class);
        return new EmbeddingResponse(Arrays.asList(embeddings));
    }
}
