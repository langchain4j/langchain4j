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
import dev.langchain4j.model.openai.internal.image.EditImageFile;
import dev.langchain4j.model.openai.internal.image.EditImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesRequest;
import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.image.ImageData;
import dev.langchain4j.model.openai.internal.image.TokenDetails;
import dev.langchain4j.model.openai.internal.image.Usage;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.openai.spi.OpenAiImageModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;

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
    private final String background;
    private final String moderation;
    private final Integer outputCompression;
    private final String outputFormat;
    private final String inputFidelity;

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
        this.style = builder.style;
        this.user = builder.user;
        this.responseFormat = builder.responseFormat;
        this.background = builder.background;
        this.moderation = builder.moderation;
        this.outputCompression = builder.outputCompression;
        this.outputFormat = builder.outputFormat;
        this.inputFidelity = builder.inputFidelity;
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<Image> generate(String prompt) {
        GenerateImagesRequest request = requestBuilder(prompt).build();

        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesGeneration(request), maxRetries)
                .execute();

        return singleImageResponse(response);
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        GenerateImagesRequest request = requestBuilder(prompt).n(n).build();

        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesGeneration(request), maxRetries)
                .execute();

        return imageListResponse(response);
    }

    /**
     * Canonical edit override. The five convenience edit overloads on {@link ImageModel}
     * delegate here with appropriate defaults, so this is the only edit method
     * {@code OpenAiImageModel} needs to override.
     */
    @Override
    public Response<List<Image>> edit(List<Image> images, Image mask, String prompt, int n) {
        EditImagesRequest request =
                editRequestBuilder(images, mask, prompt).n(n).build();
        GenerateImagesResponse response = withRetryMappingExceptions(() -> client.imagesEdit(request), maxRetries)
                .execute();
        return imageListResponse(response);
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
        private String background;
        private String moderation;
        private Integer outputCompression;
        private String outputFormat;
        private String inputFidelity;
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

        /**
         * Background of the generated image. Supported by gpt-image-* models.
         * One of {@code transparent}, {@code opaque}, {@code auto}.
         */
        public OpenAiImageModelBuilder background(String background) {
            this.background = background;
            return this;
        }

        /**
         * Moderation level. Supported by gpt-image-* models. One of {@code low}, {@code auto}.
         */
        public OpenAiImageModelBuilder moderation(String moderation) {
            this.moderation = moderation;
            return this;
        }

        /**
         * Output image compression (0-100). Supported by gpt-image-* models when
         * {@link #outputFormat(String)} is {@code webp} or {@code jpeg}.
         */
        public OpenAiImageModelBuilder outputCompression(Integer outputCompression) {
            this.outputCompression = outputCompression;
            return this;
        }

        /**
         * Output image format. Supported by gpt-image-* models. One of {@code png},
         * {@code jpeg}, {@code webp}.
         */
        public OpenAiImageModelBuilder outputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        /**
         * Input fidelity for image-edit requests. One of {@code low}, {@code high}.
         * Only consulted by {@code edit(...)}; ignored by {@code generate(...)}.
         *
         * <p>Supported by {@code gpt-image-1}. <b>Not configurable on {@code gpt-image-2}</b> —
         * that model always processes input images at high fidelity automatically and the API
         * rejects the parameter, so any value set here is silently dropped from the request when
         * the model is {@code gpt-image-2}. Ignored by {@code dall-e-*}.
         */
        public OpenAiImageModelBuilder inputFidelity(String inputFidelity) {
            this.inputFidelity = inputFidelity;
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

        public OpenAiImageModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        public OpenAiImageModel build() {
            return new OpenAiImageModel(this);
        }
    }

    private static Image fromImageData(ImageData data) {
        return Image.builder()
                .url(data.url())
                .base64Data(data.b64Json())
                .revisedPrompt(data.revisedPrompt())
                .build();
    }

    private static Response<Image> singleImageResponse(GenerateImagesResponse response) {
        Image image = fromImageData(response.data().get(0));
        TokenUsage tokenUsage = toTokenUsage(response.usage());
        return tokenUsage == null ? Response.from(image) : Response.from(image, tokenUsage);
    }

    private static Response<List<Image>> imageListResponse(GenerateImagesResponse response) {
        List<Image> images =
                response.data().stream().map(OpenAiImageModel::fromImageData).collect(Collectors.toList());
        TokenUsage tokenUsage = toTokenUsage(response.usage());
        return tokenUsage == null ? Response.from(images) : Response.from(images, tokenUsage);
    }

    /**
     * Maps the internal {@link Usage} payload to the public {@link OpenAiImageTokenUsage}.
     * Returns {@code null} when the response carries no usage block (dall-e responses).
     */
    static OpenAiImageTokenUsage toTokenUsage(Usage usage) {
        if (usage == null) {
            return null;
        }
        return OpenAiImageTokenUsage.builder()
                .inputTokenCount(usage.inputTokens())
                .outputTokenCount(usage.outputTokens())
                .totalTokenCount(usage.totalTokens())
                .inputTokensDetails(toPublicDetails(usage.inputTokensDetails()))
                .outputTokensDetails(toPublicDetails(usage.outputTokensDetails()))
                .build();
    }

    private static OpenAiImageTokenUsage.TokenDetails toPublicDetails(TokenDetails details) {
        if (details == null) {
            return null;
        }
        return new OpenAiImageTokenUsage.TokenDetails(details.textTokens(), details.imageTokens());
    }

    private GenerateImagesRequest.Builder requestBuilder(String prompt) {
        GenerateImagesRequest.Builder builder = GenerateImagesRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .size(size)
                .quality(quality)
                .style(style)
                .user(user)
                .background(background)
                .moderation(moderation)
                .outputCompression(outputCompression)
                .outputFormat(outputFormat);
        if (!isGptImageModel(modelName)) {
            builder.responseFormat(responseFormat);
        }
        return builder;
    }

    /**
     * gpt-image-* models always return base64 image data and reject the {@code response_format}
     * parameter. Detection is centralized here so both the generation and edit paths apply the
     * same rule.
     */
    static boolean isGptImageModel(String modelName) {
        return modelName != null && modelName.startsWith("gpt-image-");
    }

    EditImagesRequest.Builder editRequestBuilder(List<Image> images, Image mask, String prompt) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("images must not be null or empty");
        }
        List<EditImageFile> imageFiles = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            imageFiles.add(EditImageFile.from(images.get(i), defaultImageFileName(images.get(i), i)));
        }
        EditImageFile maskFile = mask == null ? null : EditImageFile.from(mask, "mask.png");

        EditImagesRequest.Builder builder = EditImagesRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .images(imageFiles)
                .mask(maskFile)
                .size(size)
                .quality(quality)
                .user(user)
                .background(background)
                .moderation(moderation)
                .outputFormat(outputFormat)
                .outputCompression(outputCompression);
        if (!isGptImageModel(modelName)) {
            builder.responseFormat(responseFormat);
        }
        // gpt-image-2 always processes inputs at high fidelity and the API rejects
        // input_fidelity. Drop it silently for that model; pass it through otherwise.
        if (!"gpt-image-2".equals(modelName)) {
            builder.inputFidelity(inputFidelity);
        }
        return builder;
    }

    private static String defaultImageFileName(Image image, int index) {
        String mimeType = image.mimeType();
        String extension = "png";
        if (mimeType != null) {
            extension = switch (mimeType) {
                case "image/png" -> "png";
                case "image/jpeg", "image/jpg" -> "jpg";
                case "image/webp" -> "webp";
                default -> "png";
            };
        }
        return "image-" + index + "." + extension;
    }
}
