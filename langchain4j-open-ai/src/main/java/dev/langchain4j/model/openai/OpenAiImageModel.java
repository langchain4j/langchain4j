package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.image.EditImageRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.image.ImageData;
import dev.langchain4j.model.openai.internal.image.ImageFile;
import dev.langchain4j.model.openai.spi.OpenAiImageModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Represents an OpenAI image generation model.
 * Find the parameters description <a href="https://developers.openai.com/api/reference/resources/images/methods/generate">here</a>.
 */
public class OpenAiImageModel implements ImageModel {

    private final String modelName;
    private final String size;
    private final String quality;
    private final String user;
    private final String background;
    private final String outputFormat;
    private final Integer outputCompression;
    private final String moderation;

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
                .logger(builder.logger)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(builder.customHeadersSupplier)
                .customQueryParams(builder.customQueryParams);

        this.client = cBuilder.build();

        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.modelName = builder.modelName;
        this.size = builder.size;
        this.quality = builder.quality;
        this.user = builder.user;
        this.background = builder.background;
        this.outputFormat = builder.outputFormat;
        this.outputCompression = builder.outputCompression;
        this.moderation = builder.moderation;
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<Image> generate(String prompt) {
        GenerateImagesRequest request = requestBuilder(prompt).build();

        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesGeneration(request), maxRetries)
                .execute();

        return Response.from(fromImageData(response.data().get(0), response.outputFormat()));
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        GenerateImagesRequest request = requestBuilder(prompt).n(n).build();

        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesGeneration(request), maxRetries)
                .execute();

        String responseOutputFormat = response.outputFormat();
        return Response.from(response.data().stream()
                .map(data -> fromImageData(data, responseOutputFormat))
                .collect(Collectors.toList()));
    }

    @Override
    public Response<Image> edit(Image image, String prompt) {
        EditImageRequest request = editRequestBuilder(image, prompt).build();

        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesEdit(request), maxRetries)
                .execute();

        return Response.from(fromImageData(response.data().get(0), response.outputFormat()));
    }

    @Override
    public Response<Image> edit(Image image, Image mask, String prompt) {
        EditImageRequest request =
                editRequestBuilder(image, prompt).mask(ImageFile.from(mask)).build();

        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesEdit(request), maxRetries)
                .execute();

        return Response.from(fromImageData(response.data().get(0), response.outputFormat()));
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
        private String user;
        private String background;
        private String outputFormat;
        private Integer outputCompression;
        private String moderation;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Map<String, String> customQueryParams;

        public OpenAiImageModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets a custom {@link HttpClientBuilder} used to create the HTTP client.
         * Allows full control over timeouts, proxy settings, and other HTTP client options.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public OpenAiImageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the model name, e.g. {@code "dall-e-3"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public OpenAiImageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the model name using a {@link OpenAiImageModelName} enum constant.
         *
         * @param modelName the model name enum value
         * @return {@code this}
         */
        public OpenAiImageModelBuilder modelName(OpenAiImageModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * Sets the base URL of the OpenAI-compatible API. Defaults to {@code https://api.openai.com/v1}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public OpenAiImageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the OpenAI API key used to authenticate requests.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public OpenAiImageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the OpenAI organization ID sent with each request.
         *
         * @param organizationId the organization ID
         * @return {@code this}
         */
        public OpenAiImageModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        /**
         * Sets the OpenAI project ID sent with each request.
         *
         * @param projectId the project ID
         * @return {@code this}
         */
        public OpenAiImageModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets the image size, e.g. {@code "1024x1024"}, {@code "1792x1024"}.
         *
         * @param size the image size
         * @return {@code this}
         */
        public OpenAiImageModelBuilder size(String size) {
            this.size = size;
            return this;
        }

        /**
         * Sets the image quality, e.g. {@code "standard"} or {@code "hd"}.
         *
         * @param quality the image quality
         * @return {@code this}
         */
        public OpenAiImageModelBuilder quality(String quality) {
            this.quality = quality;
            return this;
        }

        /**
         * Sets a unique end-user identifier sent to OpenAI to help monitor and detect abuse.
         *
         * @param user the end-user identifier
         * @return {@code this}
         */
        public OpenAiImageModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the background type for the generated image, e.g. {@code "transparent"} or {@code "opaque"}.
         *
         * @param background the background type
         * @return {@code this}
         */
        public OpenAiImageModelBuilder background(String background) {
            this.background = background;
            return this;
        }

        /**
         * Sets the output format for the generated image, e.g. {@code "png"}, {@code "webp"}, or {@code "jpeg"}.
         *
         * @param outputFormat the output format
         * @return {@code this}
         */
        public OpenAiImageModelBuilder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        /**
         * Sets the compression level for the output image (0–100). Only applicable to {@code webp} and {@code jpeg} formats.
         *
         * @param outputCompression the compression level
         * @return {@code this}
         */
        public OpenAiImageModelBuilder outputCompression(Integer outputCompression) {
            this.outputCompression = outputCompression;
            return this;
        }

        /**
         * Sets the moderation level for the generated image, e.g. {@code "low"} or {@code "auto"}.
         *
         * @param moderation the moderation level
         * @return {@code this}
         */
        public OpenAiImageModelBuilder moderation(String moderation) {
            this.moderation = moderation;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 15 seconds for connect and 60 seconds for read.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public OpenAiImageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public OpenAiImageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Enables or disables logging of HTTP request bodies for debugging. Defaults to {@code false}.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public OpenAiImageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables or disables logging of HTTP response bodies for debugging. Defaults to {@code false}.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public OpenAiImageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public OpenAiImageModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public OpenAiImageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public OpenAiImageModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets additional URL query parameters appended to every request.
         *
         * @param customQueryParams the query parameters map
         * @return {@code this}
         */
        public OpenAiImageModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        public OpenAiImageModel build() {
            return new OpenAiImageModel(this);
        }
    }

    private static Image fromImageData(ImageData data, String outputFormat) {
        Image.Builder imageBuilder =
                Image.builder().url(data.url()).base64Data(data.b64Json()).revisedPrompt(data.revisedPrompt());

        if (outputFormat != null) {
            imageBuilder.mimeType("image/" + outputFormat);
        }

        return imageBuilder.build();
    }

    private GenerateImagesRequest.Builder requestBuilder(String prompt) {
        return GenerateImagesRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .size(size)
                .quality(quality)
                .user(user)
                .background(background)
                .outputFormat(outputFormat)
                .outputCompression(outputCompression)
                .moderation(moderation);
    }

    private EditImageRequest.Builder editRequestBuilder(Image image, String prompt) {
        return EditImageRequest.builder()
                .image(ImageFile.from(image))
                .model(modelName)
                .prompt(prompt)
                .size(size)
                .quality(quality)
                .user(user)
                .background(background)
                .outputFormat(outputFormat)
                .outputCompression(outputCompression);
    }
}
