package dev.langchain4j.model.vertexai;

import static com.google.protobuf.Value.newBuilder;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.vertexai.Json.toJson;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.spi.VertexAiImageModelBuilderFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Image model for the Google Cloud Vertex AI Imagen image generation models.
 * Supports both versions of Imagen v1 and v2, respectively identified by the model names
 * <code>imagegeneration@002</code> and <code>imagegeneration@005</code>,
 * and will also work with the upcoming Imagen v3 model,
 * identified as <code>imagen-3.0-generate-preview-0611</code> or
 * <code>imagen-3.0-fast-generate-preview-0611</code> for faster generation.
 */
public class VertexAiImageModel implements ImageModel {

    private final Long seed;
    private final String endpoint;
    private final MimeType mimeType;
    private final Integer compressionQuality;
    private final String cloudStorageBucket;
    private final EndpointName endpointName;
    private final String language;
    private final Integer guidanceScale;
    private final String negativePrompt;
    private final ImageStyle sampleImageStyle;
    private final Integer sampleImageSize;
    private final AspectRatio aspectRatio;
    private final PersonGeneration personGeneration;
    private final Boolean addWatermark;
    private final int maxRetries;
    private final Boolean withPersisting;
    private final String modelName;
    private Path tempDirectory;
    private final Boolean logRequests;
    private final Boolean logResponses;

    private static final Logger logger = LoggerFactory.getLogger(VertexAiImageModel.class);

    /**
     * Image style can be specified for <code>imagen@002</code>.
     * For <code>imagen@005</code> (Imagen v2), specify the style in the prompt instead.
     */
    public enum ImageStyle {
        PHOTOGRAPH("photograph"),
        DIGITAL_ART("digital_art"),
        LANDSCAPE("landscape"),
        SKETCH("sketch"),
        WATERCOLOR("watercolor"),
        CYBERPUNK("cyberpunk"),
        POP_ART("pop_art");

        private final String style;

        ImageStyle(String style) {
            this.style = style;
        }

        public String toString() {
            return style;
        }
    }

    /**
     * Supported aspect ratios: 1:1, 9:16, 16:9, 4:3, and 3:4.
     * The 1:1 square format is the default aspect ratio when not specified.
     */
    public enum AspectRatio {
        SQUARE("1:1"),
        PORTRAIT("9:16"),
        LANDSCAPE("16:9"),
        THREE_FOURTHS("3:4"),
        FOUR_THIRDS("4:3");

        private final String ratio;

        AspectRatio(String ratio) {
            this.ratio = ratio;
        }

        public String toString() {
            return ratio;
        }
    }

    /**
     * Supported mime types: only PNG and JPEG image formats can be generated.
     * By default, PNG image files are generated.
     * When choosing the JPEG image, it is possible to specify a compression level.
     */
    public enum MimeType {
        PNG("image/png"),
        JPEG("image/jpeg");

        private final String mimeType;

        MimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String toString() {
            return mimeType;
        }
    }

    /**
     * Specify whether persons are allowed to be generated.
     * By default, only adults are allowed.
     * For all persons, including children, your project will need to be allowlisted.
     */
    public enum PersonGeneration {
        DONT_ALLOW("dont_allow"),
        ALLOW_ADULT("allow_adult"),
        ALLOW_ALL("allow_all");

        private final String personGeneration;

        PersonGeneration(String value) {
            this.personGeneration = value;
        }

        public String toString() {
            return personGeneration;
        }
    }

    /**
     * Constructor of the Imagen image generation model.
     *
     * @param endpoint         the base URL of the API (eg. <code>https://us-central1-aiplatform.googleapis.com/v1/</code>)
     * @param project          the Google Cloud Project ID
     * @param location         the cloud region (eg. <code>us-central1</code>)
     * @param publisher        the publisher of the model (<code>google</code> for Imagen)
     * @param modelName        the name of the image model (<code>imagegeneration@002</code>, <code>imagegeneration@005</code>,
     *                         <code>imagen-3.0-generate-preview-0611</code>, <code>imagen-3.0-fast-generate-preview-0611</code>)
     * @param seed             a fixed random seed number between 0 and 2^32-1
     * @param language         the spoken language used for the prompt
     * @param guidanceScale    an integer that represents the strength of the edit to make (0-9: low, 10-20: medium, 21+: high)
     * @param negativePrompt   a negative prompt to specify what you don't want to see in the generated image
     * @param sampleImageStyle the style of the image for Imagen v1, see the <code>ImageStyle</code> enum for reference
     * @param sampleImageSize  the size of the images to generate
     * @param aspectRatio      the aspect ratio of the image, whether square, portrait or landscape
     * @param personGeneration specify if it is allowed to generate persons (none, only adults, all)
     * @param maxRetries       number of times to retry in case of error (default: 3)
     * @param mimeType         specify the mime type of the image to generate (image/png by default, but image/jpeg possible)
     * @param compressionQuality when generating a JPEG image, specify the compression quality (ex: 80 for good quality)
     * @param addWatermark     true to generate a watermark so users can know it's an AI generated image
     *                         (default to false for Imagen v1 and v2, but to true for Imagen v3)
     * @param cloudStorageBucket URI of the Google Cloud Storage bucket where to persist the generated image
     * @param withPersisting   true if the generated images should be persisted on the local file system
     * @param persistTo        the <code>Path</code> of the directory that should contain the generated images
     */
    public VertexAiImageModel(
            String endpoint,
            String project,
            String location,
            String publisher,
            String modelName,
            Long seed,
            String language,
            Integer guidanceScale,
            String negativePrompt,
            ImageStyle sampleImageStyle,
            Integer sampleImageSize,
            AspectRatio aspectRatio,
            PersonGeneration personGeneration,
            Integer maxRetries,
            MimeType mimeType,
            Integer compressionQuality,
            Boolean addWatermark,
            String cloudStorageBucket,
            Boolean withPersisting,
            Path persistTo,
            Boolean logRequests,
            Boolean logResponses) {
        this.endpoint = ensureNotBlank(endpoint, "endpoint");

        this.endpointName = EndpointName.ofProjectLocationPublisherModelName(
                ensureNotBlank(project, "project"),
                ensureNotBlank(location, "location"),
                ensureNotBlank(publisher, "publisher"),
                ensureNotBlank(modelName, "modelName"));

        this.seed = seed == null ? null : ensureBetween(seed, 0, 4_294_967_295L, "seed");

        this.modelName = modelName;
        this.language = language;
        this.guidanceScale = guidanceScale;
        this.negativePrompt = negativePrompt;
        this.sampleImageStyle = sampleImageStyle;
        this.sampleImageSize = sampleImageSize;
        this.aspectRatio = aspectRatio;
        this.mimeType = mimeType;
        this.compressionQuality = compressionQuality;
        this.personGeneration = personGeneration;
        this.addWatermark = addWatermark;

        this.maxRetries = maxRetries == null ? 3 : maxRetries;

        this.cloudStorageBucket = cloudStorageBucket;

        this.withPersisting = withPersisting;

        // persist to persistTo directory if provided, and create it if it doesn't exist
        // otherwise create files into the temp directory provided by Java
        if (this.withPersisting != null && this.withPersisting) {
            try {
                if (persistTo != null) {
                    if (!persistTo.toFile().exists()) {
                        if (!persistTo.toFile().mkdirs()) {
                            throw new IOException("Impossible to create persistTo temporary directory");
                        }
                    }
                    tempDirectory = persistTo;
                } else {
                    tempDirectory = Files.createTempDirectory("imagen-directory-");
                }
            } catch (IOException e) {
                throw new RuntimeException("Impossible to create persistence temporary directory", e);
            }
        }

        if (logRequests != null) {
            this.logRequests = logRequests;
        } else {
            this.logRequests = false;
        }
        if (logResponses != null) {
            this.logResponses = logResponses;
        } else {
            this.logResponses = false;
        }
    }

    @Override
    public Response<Image> generate(String prompt) {
        Response<List<Image>> generatedImageResponse = generate(prompt, 1);

        return Response.from(
                generatedImageResponse.content().get(0),
                generatedImageResponse.tokenUsage(),
                generatedImageResponse.finishReason());
    }

    @Override
    public Response<List<Image>> generate(String prompt, int n) {
        return generate(prompt, null, null, n);
    }

    private Response<List<Image>> generate(String prompt, Image image, Image mask, int n) {
        try {
            PredictionServiceSettings serviceSettings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(this.endpoint)
                    .build();

            try (PredictionServiceClient client = PredictionServiceClient.create(serviceSettings)) {

                // Instance description
                List<Value> instances = prepareInstance(prompt, image, mask);

                // Parameters description
                Value parameters = prepareParameters(n);

                if (this.logRequests && logger.isDebugEnabled()) {
                    logger.debug("IMAGEN ({}) instances: {} parameters: {}", modelName, instances, parameters);
                }

                PredictResponse predictResponse = withRetryMappingExceptions(
                        () -> client.predict(this.endpointName, instances, parameters), this.maxRetries);

                if (this.logResponses && logger.isDebugEnabled()) {
                    logger.debug("IMAGEN ({}) response: {}", modelName, predictResponse);
                }

                List<Image> allImages = predictResponse.getPredictionsList().stream()
                        .filter(v -> !v.getStructValue().getFieldsMap().containsKey("raiFilteredReason"))
                        .map(v -> {
                            Map<String, Value> fieldsMap = v.getStructValue().getFieldsMap();

                            if (fieldsMap.containsKey("gcsUri")) {
                                String gcsUri = fieldsMap.get("gcsUri").getStringValue();
                                return Image.builder().url(gcsUri).build();
                            } else if (fieldsMap.containsKey("bytesBase64Encoded")) {
                                String bytesBase64Encoded =
                                        fieldsMap.get("bytesBase64Encoded").getStringValue();
                                return Image.builder()
                                        .base64Data(bytesBase64Encoded)
                                        .url(persistAndGetURI(bytesBase64Encoded))
                                        .build();
                            } else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                // if not image is generated, the generation might have been blocked because of safety filters
                if (allImages.isEmpty()) {
                    Optional<Value> raiFilteredReason = predictResponse.getPredictionsList().stream()
                            .filter(v -> v.getStructValue().getFieldsMap().containsKey("raiFilteredReason"))
                            .findFirst();
                    if (raiFilteredReason.isPresent()) {
                        String reason = raiFilteredReason
                                .get()
                                .getStructValue()
                                .getFieldsMap()
                                .get("raiFilteredReason")
                                .getStringValue();
                        throw new RuntimeException("Image generation blocked for safaty reasons: " + reason);
                    } else {
                        throw new RuntimeException(
                                "No image was generated. The image generation might have been blocked.");
                    }
                }

                return Response.from(allImages);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Value prepareParameters(int n) throws InvalidProtocolBufferException {
        Map<String, Object> paramsMap = new HashMap<>();

        paramsMap.put("sampleCount", n);

        paramsMap.put("includeRaiReason", true);
        paramsMap.put("includeSafetyAttributes", true);
        //        paramsMap.put("safetySettings", "block_fewest"); // TODO: only available in the upcoming models

        if (this.seed != null) {
            paramsMap.put("seed", this.seed);
        }

        if (this.sampleImageStyle != null) {
            paramsMap.put("sampleImageStyle", this.sampleImageStyle.toString());
        }

        if (this.sampleImageSize != null) {
            paramsMap.put("mode", "upscale");
            paramsMap.put("sampleImageSize", this.sampleImageSize.toString());
        }

        if (this.guidanceScale != null) {
            paramsMap.put("guidanceScale", this.guidanceScale);
        }

        if (this.negativePrompt != null) {
            paramsMap.put("negativePrompt", this.negativePrompt);
        }

        if (this.language != null) {
            paramsMap.put("language", this.language);
        }

        if (this.aspectRatio != null) {
            paramsMap.put("aspectRatio", this.aspectRatio.toString());
        }

        if (this.mimeType != null) {
            Map<String, Object> outputOptions = new HashMap<>();
            outputOptions.put("mimeType", this.mimeType.toString());
            if (this.mimeType == MimeType.JPEG && this.compressionQuality != null) {
                outputOptions.put("compressionQuality", this.compressionQuality);
            }

            paramsMap.put("outputOptions", outputOptions);
        }

        if (this.personGeneration != null) {
            paramsMap.put("personGeneration", this.personGeneration.toString());
        }

        if (this.addWatermark != null) {
            paramsMap.put("addWatermark", this.addWatermark);
        }

        if (this.cloudStorageBucket != null) {
            paramsMap.put("storageUri", this.cloudStorageBucket);
        }

        Value.Builder parametersBuilder = Value.newBuilder();
        JsonFormat.parser().merge(toJson(paramsMap), parametersBuilder);
        return parametersBuilder.build();
    }

    private List<Value> prepareInstance(String prompt, Image image, Image mask) throws InvalidProtocolBufferException {
        Map<String, Object> promptMap = new HashMap<>();
        promptMap.put("prompt", prompt);

        if (image != null && image.base64Data() != null) {
            Map<String, String> imageMap = new HashMap<>();
            imageMap.put("bytesBase64Encoded", image.base64Data());
            promptMap.put("image", imageMap);
        }

        if (mask != null && mask.base64Data() != null) {
            Map<String, String> imageMap = new HashMap<>();
            imageMap.put("bytesBase64Encoded", mask.base64Data());

            Map<String, Map<String, String>> maskMap = new HashMap<>();
            maskMap.put("image", imageMap);

            promptMap.put("mask", maskMap);
        }

        Value.Builder instanceBuilder = newBuilder();
        JsonFormat.parser().merge(toJson(promptMap), instanceBuilder);
        return singletonList(instanceBuilder.build());
    }

    @Override
    public Response<Image> edit(Image image, String prompt) {
        Response<Image> generatedImageResponse = edit(image, null, prompt);
        return Response.from(
                generatedImageResponse.content(),
                generatedImageResponse.tokenUsage(),
                generatedImageResponse.finishReason());
    }

    @Override
    public Response<Image> edit(Image image, Image mask, String prompt) {
        Response<List<Image>> generatedImageResponse = generate(prompt, image, mask, 1);
        return Response.from(
                generatedImageResponse.content().get(0),
                generatedImageResponse.tokenUsage(),
                generatedImageResponse.finishReason());
    }

    private URI persistAndGetURI(String bytesBase64Encoded) {
        if (this.withPersisting != null && this.withPersisting) {
            try {
                String suffix = ".png";
                if (this.mimeType == MimeType.JPEG) {
                    suffix = ".jpg";
                }

                Path tempFile = Files.createTempFile(this.tempDirectory, "imagen-image-", suffix);
                Files.write(tempFile, Base64.getDecoder().decode(bytesBase64Encoded));
                return tempFile.toUri();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    public static Builder builder() {
        for (VertexAiImageModelBuilderFactory factory : loadFactories(VertexAiImageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {
        private String endpoint;
        private String project;
        private String location;
        private String publisher;
        private String modelName;
        private Long seed;
        private String language;
        private String negativePrompt;
        private ImageStyle sampleImageStyle;
        private AspectRatio aspectRatio;
        private Integer sampleImageSize;
        private Integer maxRetries;
        private Integer guidanceScale;
        private MimeType mimeType;
        private PersonGeneration personGeneration;
        private Boolean watermark;
        private Boolean withPersisting;
        private Path persistTo;
        private Integer compressionQuality;
        private String cloudStorageBucket;
        private Boolean logRequests;
        private Boolean logResponses;

        /**
         * Sets the Vertex AI API endpoint, e.g. {@code "us-central1-aiplatform.googleapis.com:443"}.
         *
         * @param endpoint the API endpoint
         * @return {@code this}
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Google Cloud project ID.
         *
         * @param project the project ID
         * @return {@code this}
         */
        public Builder project(String project) {
            this.project = project;
            return this;
        }

        /**
         * Sets the Google Cloud region, e.g. {@code "us-central1"}.
         *
         * @param location the cloud region
         * @return {@code this}
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * Sets the model publisher. Use {@code "google"} for Vertex AI first-party models.
         *
         * @param publisher the publisher name
         * @return {@code this}
         */
        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        /**
         * Sets the Imagen model name, e.g. {@code "imagegeneration@006"} or
         * {@code "imagen-3.0-generate-001"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets a fixed random seed for deterministic image generation.
         * Must be in the range {@code [0, 4294967295]}.
         *
         * @param seed the random seed
         * @return {@code this}
         */
        public Builder seed(Long seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets the spoken language of the prompt text, e.g. {@code "en"} or {@code "fr"}.
         *
         * @param language the BCP-47 language code
         * @return {@code this}
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * Sets the guidance scale, which controls how closely the image follows the prompt.
         * Low guidance (0–9) gives more creative freedom; high guidance (21+) adheres more strictly.
         *
         * @param guidanceScale the guidance scale value
         * @return {@code this}
         */
        public Builder guidanceScale(Integer guidanceScale) {
            this.guidanceScale = guidanceScale;
            return this;
        }

        /**
         * Sets a negative prompt describing what to exclude from the generated image.
         *
         * @param negativePrompt the negative prompt text
         * @return {@code this}
         */
        public Builder negativePrompt(String negativePrompt) {
            this.negativePrompt = negativePrompt;
            return this;
        }

        /**
         * Sets the artistic style for Imagen v1 ({@code imagegeneration@002}).
         * For Imagen v2 and later, specify the style in the prompt text instead.
         *
         * @param sampleImageStyle the image style
         * @return {@code this}
         */
        public Builder sampleImageStyle(ImageStyle sampleImageStyle) {
            this.sampleImageStyle = sampleImageStyle;
            return this;
        }

        /**
         * Sets the upscale target size in pixels for upscaling mode.
         *
         * @param sampleImageSize the image size in pixels
         * @return {@code this}
         */
        public Builder sampleImageSize(Integer sampleImageSize) {
            this.sampleImageSize = sampleImageSize;
            return this;
        }

        /**
         * Sets the aspect ratio of the generated image. Defaults to {@code AspectRatio.SQUARE} (1:1).
         *
         * @param aspectRatio the desired aspect ratio
         * @return {@code this}
         */
        public Builder aspectRatio(AspectRatio aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }

        /**
         * Sets the MIME type of the generated image. Defaults to {@code MimeType.PNG}.
         *
         * @param mimeType the output MIME type
         * @return {@code this}
         */
        public Builder mimeType(MimeType mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        /**
         * Sets the JPEG compression quality when {@code mimeType} is {@code MimeType.JPEG},
         * e.g. {@code 80} for good quality.
         *
         * @param compressionQuality the JPEG compression quality (0–100)
         * @return {@code this}
         */
        public Builder compressionQuality(Integer compressionQuality) {
            this.compressionQuality = compressionQuality;
            return this;
        }

        /**
         * Sets whether persons are allowed to appear in generated images.
         * Defaults to {@code PersonGeneration.ALLOW_ADULT}.
         *
         * @param personGeneration the person-generation policy
         * @return {@code this}
         */
        public Builder personGeneration(PersonGeneration personGeneration) {
            this.personGeneration = personGeneration;
            return this;
        }

        /**
         * When {@code true}, adds an invisible watermark to the generated image to indicate
         * it was AI-generated. Defaults to {@code false} for Imagen v1/v2, {@code true} for Imagen v3.
         *
         * @param watermark {@code true} to add a watermark
         * @return {@code this}
         */
        public Builder watermark(Boolean watermark) {
            this.watermark = watermark;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 3}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the Google Cloud Storage URI where generated images should be persisted,
         * e.g. {@code "gs://my-bucket/images"}.
         *
         * @param gcsUri the GCS bucket URI
         * @return {@code this}
         */
        public Builder persistToCloudStorage(String gcsUri) {
            this.cloudStorageBucket = gcsUri;
            return this;
        }

        /**
         * Enables persisting generated images to the local file system.
         * Use {@link #persistTo(Path)} to specify a target directory, or images will be written
         * to a temporary directory.
         *
         * @return {@code this}
         */
        public Builder withPersisting() {
            this.withPersisting = Boolean.TRUE;
            return this;
        }

        /**
         * Sets the local directory where generated images are saved, and implicitly enables persisting.
         * The directory is created if it does not exist.
         *
         * @param persistTo the target directory path
         * @return {@code this}
         */
        public Builder persistTo(Path persistTo) {
            this.persistTo = persistTo;
            return withPersisting();
        }

        /**
         * Enables debug logging of image generation request details.
         *
         * @param logRequests {@code true} to enable request logging
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of image generation response details.
         *
         * @param logResponses {@code true} to enable response logging
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public VertexAiImageModel build() {
            return new VertexAiImageModel(
                    this.endpoint,
                    this.project,
                    this.location,
                    this.publisher,
                    this.modelName,
                    this.seed,
                    this.language,
                    this.guidanceScale,
                    this.negativePrompt,
                    this.sampleImageStyle,
                    this.sampleImageSize,
                    this.aspectRatio,
                    this.personGeneration,
                    this.maxRetries,
                    this.mimeType,
                    this.compressionQuality,
                    this.watermark,
                    this.cloudStorageBucket,
                    this.withPersisting,
                    this.persistTo,
                    this.logRequests,
                    this.logResponses);
        }
    }
}
