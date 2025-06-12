package dev.langchain4j.model.openai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.image.ImageData;
import dev.langchain4j.model.openai.spi.OpenAiImageModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI DALL·E models to generate artistic images. Versions 2 and 3 (default) are supported.
 * Find the parameters description <a href="https://platform.openai.com/docs/api-reference/images/create">here</a>.
 */
public class OpenAiImageModel implements ImageModel {

    private final String modelName;
    private final String size;
    private final String quality;
    private final String style;
    private final String user;
    private final String responseFormat;

    private final OpenAiClient client;

    private final Integer maxRetries;

    public OpenAiImageModel(OpenAiImageModelBuilder builder) {
        OpenAiClient.Builder cBuilder = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_OPENAI_URL))
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(builder.customHeaders);

        this.client = cBuilder.build();

        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.modelName = builder.modelName;
        this.size = builder.size;
        this.quality = builder.quality;
        this.style = builder.style;
        this.user = builder.user;
        this.responseFormat = builder.responseFormat;
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<Image> generate(String prompt) {
        GenerateImagesRequest request = requestBuilder(prompt).build();

        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesGeneration(request), maxRetries).execute();

        return Response.from(fromImageData(response.data().get(0)));
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        GenerateImagesRequest request = requestBuilder(prompt).n(n).build();

        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesGeneration(request), maxRetries).execute();

        return Response.from(
                response.data().stream().map(OpenAiImageModel::fromImageData).collect(Collectors.toList())
        );
    }

    public static OpenAiImageModelBuilder builder() {
        for (OpenAiImageModelBuilderFactory factory : loadFactories(OpenAiImageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiImageModelBuilder();
    }

    public static class OpenAiImageModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;

        private String modelName;
        private String size;
        private String quality;
        private String style;
        private String user;
        private String responseFormat;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public OpenAiImageModelBuilder() {
            // This is public so it can be extended
        }

        public OpenAiImageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OpenAiImageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiImageModelBuilder modelName(OpenAiImageModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiImageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiImageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiImageModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiImageModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public OpenAiImageModelBuilder size(String size) {
            this.size = size;
            return this;
        }

        public OpenAiImageModelBuilder quality(String quality) {
            this.quality = quality;
            return this;
        }

        public OpenAiImageModelBuilder style(String style) {
            this.style = style;
            return this;
        }

        public OpenAiImageModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiImageModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OpenAiImageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiImageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiImageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiImageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OpenAiImageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiImageModel build() {
            return new OpenAiImageModel(this);
        }
    }

    private static Image fromImageData(ImageData data) {
        return Image.builder().url(data.url()).base64Data(data.b64Json()).revisedPrompt(data.revisedPrompt()).build();
    }

    private GenerateImagesRequest.Builder requestBuilder(String prompt) {
        return GenerateImagesRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .size(size)
                .quality(quality)
                .style(style)
                .user(user)
                .responseFormat(responseFormat);
    }
}
