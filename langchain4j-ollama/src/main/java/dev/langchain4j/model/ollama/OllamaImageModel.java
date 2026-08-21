package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZeroIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.ollama.spi.OllamaImageModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Experimental Ollama image generation model.
 * <p>
 * Supports text-to-image generation through Ollama's {@code /api/generate} endpoint. Generated images
 * are returned as PNG Base64 data. Image editing and multi-image generation are not supported.
 *
 * @see <a href="https://github.com/ollama/ollama/blob/main/docs/api.md#image-generation-experimental">Ollama image generation API</a>
 */
@Experimental
public class OllamaImageModel implements ImageModel {

    private static final String IMAGE_PNG = "image/png";
    private static final int MAX_IMAGE_DIMENSION = 4096;

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final Integer width;
    private final Integer height;
    private final Integer steps;
    private final Integer maxRetries;

    public OllamaImageModel(OllamaImageModelBuilder builder) {
        this.client = OllamaClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .customHeaders(builder.customHeadersSupplier)
                .build();
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.options = Options.builder().seed(builder.seed).build();
        this.width = ensureDimension(builder.width, "width");
        this.height = ensureDimension(builder.height, "height");
        this.steps = ensureSteps(builder.steps);
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    public static OllamaImageModelBuilder builder() {
        for (OllamaImageModelBuilderFactory factory : loadFactories(OllamaImageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaImageModelBuilder();
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<Image> generate(String prompt) {
        CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(ensureNotBlank(prompt, "prompt"))
                .options(options)
                .width(width)
                .height(height)
                .steps(steps)
                .stream(false)
                .build();

        CompletionResponse response = withRetryMappingExceptions(() -> client.completion(request), maxRetries);

        return Response.from(fromResponse(response));
    }

    private static Image fromResponse(CompletionResponse response) {
        String image = response.getImage();
        if (isNullOrBlank(image)) {
            throw new OllamaImageGenerationException("No image was returned by Ollama");
        }

        return Image.builder().base64Data(image).mimeType(IMAGE_PNG).build();
    }

    private static Integer ensureDimension(Integer dimension, String name) {
        if (dimension == null || dimension == 0) {
            return null;
        }
        return ensureBetween(dimension, 0, MAX_IMAGE_DIMENSION, name);
    }

    private static Integer ensureSteps(Integer steps) {
        if (steps == null || steps == 0) {
            return null;
        }
        return ensureGreaterThanZeroIfNotNull(steps, "steps");
    }

    public static class OllamaImageModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String modelName;
        private Integer width;
        private Integer height;
        private Integer steps;
        private Integer seed;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Supplier<Map<String, String>> customHeadersSupplier;

        public OllamaImageModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets the {@link HttpClientBuilder} that will be used to create the {@link HttpClient}
         * that will be used to communicate with Ollama.
         * <p>
         * NOTE: {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}.
         */
        public OllamaImageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OllamaImageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OllamaImageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OllamaImageModelBuilder width(Integer width) {
            this.width = width;
            return this;
        }

        public OllamaImageModelBuilder height(Integer height) {
            this.height = height;
            return this;
        }

        public OllamaImageModelBuilder steps(Integer steps) {
            this.steps = steps;
            return this;
        }

        public OllamaImageModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OllamaImageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OllamaImageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OllamaImageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OllamaImageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public OllamaImageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public OllamaImageModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        public OllamaImageModel build() {
            return new OllamaImageModel(this);
        }
    }

    /**
     * Exception thrown when Ollama does not return an image.
     */
    public static class OllamaImageGenerationException extends RuntimeException {

        public OllamaImageGenerationException(String message) {
            super(message);
        }
    }
}
