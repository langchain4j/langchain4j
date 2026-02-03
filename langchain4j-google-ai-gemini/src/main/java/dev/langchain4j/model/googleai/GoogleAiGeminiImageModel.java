package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.googleai.GeminiResponseModality.IMAGE;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiBlob;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiFileData;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool.GeminiGoogleSearchRetrieval;
import dev.langchain4j.model.googleai.GeminiGenerationConfig.GeminiImageConfig;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

/**
 * Represents a Google AI Gemini model for image generation and editing.
 *
 * <p>Gemini can generate and process images conversationally. You can prompt either the fast
 * Gemini 2.5 Flash Image (Nano Banana) or the advanced Gemini 3 Pro Preview image (Nano Banana Pro).</p>
 *
 * <h2>Key Capabilities</h2>
 * <ul>
 *   <li><strong>Text-to-Image:</strong> Generate high-quality images from text descriptions.</li>
 *   <li><strong>Image Editing:</strong> Use text prompts to edit and adjust a given image, or use
 *       multiple input images to compose new scenes and transfer styles.</li>
 *   <li><strong>High-Fidelity Text Rendering:</strong> Accurately generate images that contain
 *       legible and well-placed text, ideal for logos, diagrams, and posters.</li>
 * </ul>
 *
 * <p>All generated images include a
 * <a href="https://ai.google.dev/responsible/docs/safeguards/synthid">SynthID watermark</a>.</p>
 *
 * <h2>Supported Models</h2>
 * <ul>
 *   <li><strong>gemini-2.5-flash-image:</strong> Optimized for speed and efficiency,
 *       ideal for high-volume, low-latency tasks. Generates images at 1024px resolution.</li>
 *   <li><strong>gemini-3-pro-image-preview</strong> Designed for professional asset production
 *       with advanced reasoning, Google Search grounding, and up to 4K resolution output.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * GoogleAiGeminiImageModel model = GoogleAiGeminiImageModel.builder()
 *     .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
 *     .modelName("gemini-2.5-flash-image")
 *     .aspectRatio("16:9")
 *     .build();
 *
 * // Generate an image
 * Response<Image> response = model.generate("A serene mountain landscape at sunset");
 *
 * // Edit an existing image
 * Response<Image> edited = model.edit(originalImage, "Add a hot air balloon to the sky");
 * }</pre>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>For best performance, use supported languages: EN, ar-EG, de-DE, es-MX, fr-FR, hi-IN,
 *       id-ID, it-IT, ja-JP, ko-KR, pt-BR, ru-RU, ua-UA, vi-VN, zh-CN.</li>
 *   <li>Image generation does not support audio or video inputs.</li>
 *   <li>gemini-2.5-flash-image works best with up to 3 images as input, while gemini-3-pro-image-preview
 *       supports up to 14 reference images.</li>
 * </ul>
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/image-generation">Gemini Image Generation Documentation</a>
 */
// TODO: Add support for reference images to #generate().
// TODO: Add support for multimodality (Both IMAGE and TEXT are supported as output format).
@Experimental
public class GoogleAiGeminiImageModel implements ImageModel {

    private final String modelName;
    private final GeminiImageConfig imageConfig;
    private final List<GeminiResponseModality> responseModalities;

    private final GeminiService geminiService;
    private final Integer maxRetries;
    private final List<GeminiSafetySetting> safetySettings;
    private final GeminiTool tools;

    private GoogleAiGeminiImageModel(GoogleAiGeminiImageModelBuilder builder) {

        this.geminiService = new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                getOrDefault(builder.logRequestsAndResponses, false),
                getOrDefault(builder.logRequests, false),
                getOrDefault(builder.logResponses, false),
                builder.logger,
                builder.timeout);

        this.modelName = ensureNotNull(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.responseModalities = List.of(IMAGE); // TEXT is not supported as an output modality.
        this.safetySettings = builder.safetySettings;

        if (getOrDefault(builder.useGoogleSearchGrounding, false)) {
            this.tools = new GeminiTool(null, null, new GeminiGoogleSearchRetrieval(), null, null);
        } else {
            this.tools = null;
        }

        // Build imageConfig if aspectRatio or imageSize is set
        if (builder.aspectRatio != null || builder.imageSize != null) {
            this.imageConfig = GeminiImageConfig.builder()
                    .aspectRatio(builder.aspectRatio)
                    .imageSize(builder.imageSize)
                    .build();
        } else {
            this.imageConfig = null;
        }
    }

    /**
     * Returns a new builder for constructing GoogleAiGeminiImageModel instances.
     */
    public static GoogleAiGeminiImageModelBuilder builder() {
        return new GoogleAiGeminiImageModelBuilder();
    }

    /**
     * Returns the model name used for image generation.
     */
    public String modelName() {
        return modelName;
    }

    /**
     * Generates an image from a text prompt.
     *
     * <p>Example prompts:</p>
     * <ul>
     *   <li>"A photorealistic close-up portrait of an elderly Japanese ceramicist"</li>
     *   <li>"A kawaii-style sticker of a happy red panda wearing a tiny bamboo hat"</li>
     *   <li>"Create a modern, minimalist logo for a coffee shop called 'The Daily Grind'"</li>
     * </ul>
     *
     * @param prompt the text description of the image to generate
     * @return a Response containing the generated Image with base64 data and MIME type
     * @throws GeminiImageGenerationException if no image is generated or an error occurs
     */
    @Override
    public Response<@NonNull Image> generate(String prompt) {
        var request = createGenerateRequest(prompt);
        var response = withRetryMappingExceptions(() -> geminiService.generateContent(modelName, request), maxRetries);

        return toResponse(response);
    }

    /**
     * Edits an existing image based on a text prompt.
     *
     * <p>This method allows you to add, remove, or modify elements in an image using natural
     * language instructions. The model will match the original image's style, lighting, and
     * perspective when making changes.</p>
     *
     * <p>Example prompts:</p>
     * <ul>
     *   <li>"Add a small wizard hat on the cat's head"</li>
     *   <li>"Change the blue sofa to a brown leather chesterfield"</li>
     *   <li>"Transform this photo into Van Gogh's Starry Night style"</li>
     * </ul>
     *
     * @param image  the image to edit (must have base64Data set, URL-based images are not supported)
     * @param prompt the text description of the edit to apply
     * @return a Response containing the edited Image
     * @throws IllegalArgumentException       if image is null or prompt is blank
     * @throws GeminiImageGenerationException if the image has no base64 data or editing fails
     */
    @Override
    public Response<@NonNull Image> edit(Image image, String prompt) {
        ensureNotNull(image, "image");
        ensureNotBlank(prompt, "prompt");

        var request = createEditRequest(prompt, image, null);
        var response = withRetryMappingExceptions(() -> geminiService.generateContent(modelName, request), maxRetries);

        return toResponse(response);
    }

    /**
     * Edits an existing image using a mask to specify the area to modify.
     *
     * <p>This method allows for more precise inpainting by providing a mask image that defines
     * which areas of the original image should be modified. The mask and edit prompt work
     * together to produce targeted changes while preserving the rest of the image.</p>
     *
     * @param image  the image to edit (must have base64Data set)
     * @param mask   the mask image defining the area to edit (must have base64Data set)
     * @param prompt the text description of the edit to apply to the masked area
     * @return a Response containing the edited Image
     * @throws IllegalArgumentException       if image, mask is null or prompt is blank
     * @throws GeminiImageGenerationException if editing fails
     */
    @Override
    public Response<@NonNull Image> edit(Image image, Image mask, String prompt) {
        ensureNotNull(image, "image");
        ensureNotNull(mask, "mask");
        ensureNotBlank(prompt, "prompt");

        var request = createEditRequest(prompt, image, mask);
        var response = withRetryMappingExceptions(() -> geminiService.generateContent(modelName, request), maxRetries);

        return toResponse(response);
    }

    private Response<Image> toResponse(GeminiGenerateContentResponse response) {
        Image image = extractImage(response);

        TokenUsage tokenUsage = null;
        if (response.usageMetadata() != null) {
            tokenUsage = new TokenUsage(
                    response.usageMetadata().promptTokenCount(),
                    response.usageMetadata().candidatesTokenCount(),
                    response.usageMetadata().totalTokenCount());
        }

        FinishReason finishReason = null;
        if (response.candidates().get(0).finishReason() != null) {
            finishReason = FinishReasonMapper.fromGFinishReasonToFinishReason(
                    response.candidates().get(0).finishReason());
        }

        Map<String, Object> metadata = new HashMap<>();
        GroundingMetadata groundingMetadata = response.groundingMetadata();
        if (groundingMetadata == null && !response.candidates().isEmpty()) {
            groundingMetadata = response.candidates().get(0).groundingMetadata();
        }

        if (groundingMetadata != null) {
            Map<String, Object> groundingMetadataMap = Json.convertValue(groundingMetadata, new TypeReference<>() {});
            metadata.put("groundingMetadata", groundingMetadataMap);
        }

        return Response.from(image, tokenUsage, finishReason, metadata);
    }

    private GeminiGenerateContentRequest createGenerateRequest(String prompt) {
        var content = new GeminiContent(List.of(GeminiPart.ofText(prompt)), GeminiRole.USER.toString());

        return createGenerateContentRequest(content);
    }

    private GeminiGenerateContentRequest createEditRequest(String prompt, Image image, Image mask) {
        var parts = new ArrayList<GeminiPart>();

        // Add text prompt first
        parts.add(GeminiPart.ofText(prompt));

        // Add the main image
        parts.add(createImagePart(image));

        // Add mask if provided
        if (mask != null) {
            parts.add(createImagePart(mask));
        }

        var content = new GeminiContent(parts, GeminiRole.USER.toString());

        return createGenerateContentRequest(content);
    }

    private GeminiGenerateContentRequest createGenerateContentRequest(GeminiContent content) {
        return GeminiGenerateContentRequest.builder()
                .contents(List.of(content))
                .generationConfig(createGenerationConfig())
                .safetySettings(safetySettings)
                .tools(tools)
                .build();
    }

    private GeminiPart createImagePart(Image image) {
        String base64Data = image.base64Data();
        String mimeType = image.mimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/png"; // Default to PNG if not specified
        }

        if (base64Data == null && image.url() != null) {
            return GeminiPart.builder()
                    .fileData(new GeminiFileData(mimeType, image.url().toString()))
                    .build();
        }

        return GeminiPart.builder()
                .inlineData(new GeminiBlob(mimeType, ensureNotBlank(base64Data, "image.base64Data")))
                .build();
    }

    private GeminiGenerationConfig createGenerationConfig() {
        return GeminiGenerationConfig.builder()
                .responseModalities(responseModalities)
                .imageConfig(imageConfig)
                .build();
    }

    private Image extractImage(GeminiGenerateContentResponse response) {
        if (response.candidates() == null || response.candidates().isEmpty()) {
            throw new GeminiImageGenerationException("No image generated in response");
        }

        GeminiGenerateContentResponse.GeminiCandidate candidate =
                response.candidates().get(0);
        if (candidate.content() == null || candidate.content().parts() == null) {
            throw new GeminiImageGenerationException("No content in response candidate");
        }

        for (GeminiPart part : candidate.content().parts()) {
            if (part.inlineData() != null) {
                return Image.builder()
                        .base64Data(part.inlineData().data())
                        .mimeType(part.inlineData().mimeType())
                        .build();
            }
        }

        throw new GeminiImageGenerationException("No image data found in response");
    }

    /**
     * Builder for constructing {@link GoogleAiGeminiImageModel} instances.
     */
    @SuppressWarnings("unused")
    public static class GoogleAiGeminiImageModelBuilder {
        private HttpClientBuilder httpClientBuilder;
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private String aspectRatio;
        private String imageSize;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequestsAndResponses;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private List<GeminiSafetySetting> safetySettings;
        private Boolean useGoogleSearchGrounding;

        private GoogleAiGeminiImageModelBuilder() {}

        /**
         * Sets the HTTP client builder for custom HTTP configuration.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the API key for authenticating with the Gemini API.
         *
         * @param apiKey the Google AI API key (required)
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets a custom base URL for the Gemini API.
         *
         * @param baseUrl the base URL (optional, defaults to Google's API endpoint)
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the model name to use for image generation.
         *
         * <p>Supported models:</p>
         * <ul>
         *   <li>{@code gemini-2.5-flash-preview-image-generation} (default) - Fast, efficient generation</li>
         *   <li>{@code gemini-3-pro-image-preview} - Advanced capabilities, up to 4K resolution</li>
         * </ul>
         *
         * @param modelName the model name
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the aspect ratio for generated images.
         *
         * <p>Supported aspect ratios: "1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9"</p>
         *
         * <p>If not specified, the model defaults to matching the input image size or generating 1:1 squares.</p>
         *
         * @param aspectRatio the aspect ratio (e.g., "16:9")
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder aspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        /**
         * Sets the image size/resolution for generated images.
         *
         * <p>Supported sizes (for gemini-3-pro-image-preview only): "1K", "2K", "4K"</p>
         *
         * <p>Note: You must use an uppercase 'K'. Lowercase parameters will be rejected.</p>
         *
         * @param imageSize the image size (e.g., "2K")
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder imageSize(String imageSize) {
            this.imageSize = imageSize;
            return this;
        }

        /**
         * Sets the timeout for API requests.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries for failed requests.
         *
         * @param maxRetries the maximum number of retries (default: 2)
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Enables or disables logging of both requests and responses.
         *
         * @param logRequestsAndResponses true to enable logging
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder logRequestsAndResponses(Boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        /**
         * Enables or disables logging of requests only.
         *
         * @param logRequests true to enable request logging
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables or disables logging of responses only.
         *
         * @param logResponses true to enable response logging
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets a custom logger for request/response logging.
         *
         * @param logger the SLF4J logger to use
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets safety settings to control content filtering.
         *
         * @param safetySettings the list of safety settings
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder safetySettings(List<GeminiSafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        /**
         * Enables or disables Google Search grounding.
         *
         * @param useGoogleSearchGrounding true to enable Google Search grounding
         * @return this builder
         */
        public GoogleAiGeminiImageModelBuilder useGoogleSearchGrounding(Boolean useGoogleSearchGrounding) {
            this.useGoogleSearchGrounding = useGoogleSearchGrounding;
            return this;
        }

        /**
         * Builds a new {@link GoogleAiGeminiImageModel} instance.
         *
         * @return the configured image model
         * @throws IllegalArgumentException if required parameters are missing
         */
        public GoogleAiGeminiImageModel build() {
            return new GoogleAiGeminiImageModel(this);
        }
    }

    /**
     * Exception thrown when image generation or editing fails.
     */
    public static class GeminiImageGenerationException extends RuntimeException {

        /**
         * Creates a new exception with the specified message.
         */
        public GeminiImageGenerationException(String message) {
            super(message);
        }
    }
}
