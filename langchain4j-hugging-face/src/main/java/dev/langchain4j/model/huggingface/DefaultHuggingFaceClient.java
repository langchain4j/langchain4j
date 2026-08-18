package dev.langchain4j.model.huggingface;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.huggingface.HuggingFaceJsonUtils.fromJson;
import static dev.langchain4j.model.huggingface.HuggingFaceJsonUtils.toJson;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.HuggingFaceClient;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

class DefaultHuggingFaceClient implements HuggingFaceClient {

    private static final String BASE_URL = "https://router.huggingface.co/hf-inference/";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String modelId;

    DefaultHuggingFaceClient(
            HttpClientBuilder httpClientBuilder, String baseUrl, String apiKey, String modelId, Duration timeout) {

        HttpClientBuilder builder = getOrDefault(httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        this.httpClient = builder.connectTimeout(timeout).readTimeout(timeout).build();

        this.baseUrl = ensureTrailingForwardSlash(Objects.isNull(baseUrl) ? BASE_URL : baseUrl);
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.modelId = ensureNotBlank(modelId, "modelId");
    }

    @Override
    public TextGenerationResponse chat(TextGenerationRequest request) {
        return generate(request);
    }

    @Override
    public TextGenerationResponse generate(TextGenerationRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "models/" + modelId)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse httpResponse = httpClient.execute(httpRequest);

        List<TextGenerationResponse> responses =
                fromJson(httpResponse.body(), new TypeReference<List<TextGenerationResponse>>() {});

        return toOneResponse(responses);
    }

    private static TextGenerationResponse toOneResponse(List<TextGenerationResponse> responses) {
        if (responses != null && responses.size() == 1) {
            return responses.get(0);
        } else {
            throw new RuntimeException(
                    "Expected only one generated_text, but was: " + (responses == null ? 0 : responses.size()));
        }
    }

    @Override
    public List<float[]> embed(EmbeddingRequest request) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "models/" + modelId + "/pipeline/feature-extraction")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .body(toJson(request))
                .build();

        SuccessfulHttpResponse httpResponse = httpClient.execute(httpRequest);

        return fromJson(httpResponse.body(), new TypeReference<List<float[]>>() {});
    }
}
