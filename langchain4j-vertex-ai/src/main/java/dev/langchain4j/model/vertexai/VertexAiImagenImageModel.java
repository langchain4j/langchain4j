package dev.langchain4j.model.vertexai;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.protobuf.Value.newBuilder;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.singletonList;

/**
 * Image model for the Google Cloud Vertex AI Imagen image generation models.
 * Supports both versions of Imagen v1 and v2, respectively identified by the model names
 * <code>imagegeneration@002</code> and <code>imagegeneration@005</code>.
 */
public class VertexAiImagenImageModel implements ImageModel {

    private final Long seed;
    private final String endpoint;
    private final EndpointName endpointName;
    private final String language;
    private final Image image;
    private final Image mask;
    private final Integer guidanceScale;
    private final String negativePrompt;
    private final ImageStyle sampleImageStyle;
    private final Integer sampleImageSize;
    private final int maxRetries;
    private final Boolean withPersisting;
    private Path tempDirectory;

    /**
     * Image style can be specified for imagen@002.
     * For imagen@005 (Imagen v2), specify the style in the prompt instead.
     */
    public enum ImageStyle {
        photograph,
        digital_art,
        landscape,
        sketch,
        watercolor,
        cyberpunk,
        pop_art
    }

    /**
     * Constructor of the Imagen image generation model.
     *
     * @param endpoint         the base URL of the API (eg. <code>https://us-central1-aiplatform.googleapis.com/v1/</code>)
     * @param project          the Google Cloud Project ID
     * @param location         the cloud region (eg. <code>us-central1</code>)
     * @param publisher        the publisher of the model (<code>google</code> for Imagen)
     * @param modelName        the name of the image model (<code>imagegeneration@002</code> or <code>imagegeneration@005</code>)
     * @param seed             a fixed random seed number between 0 and 2^32-1
     * @param language         the spoken language used for the prompt
     * @param image            an image to edit
     * @param mask             a mask image to specify which parts of the image to edit
     * @param guidanceScale    an integer that represents the strength of the edit to make (0-9: low, 10-20: medium, 21+: high)
     * @param negativePrompt   a negative prompt to specify what you don't want to see in the generated image
     * @param sampleImageStyle the style of the image for Imagen v1, see the <code>ImageStyle</code> enum for reference
     * @param sampleImageSize  the size of the images to generate
     * @param maxRetries       number of times to retry in case of error (default: 3)
     * @param withPersisting   true if the generated images should be persisted on the local file system
     * @param persistTo        the <code>Path</code> of the directory that should contain the generated images
     */
    public VertexAiImagenImageModel(String endpoint,
                                    String project,
                                    String location,
                                    String publisher,
                                    String modelName,
                                    Long seed,
                                    String language,
                                    Image image,
                                    Image mask,
                                    Integer guidanceScale,
                                    String negativePrompt,
                                    ImageStyle sampleImageStyle,
                                    Integer sampleImageSize,
                                    Integer maxRetries,
                                    Boolean withPersisting,
                                    Path persistTo
    ) {
        this.endpoint = ensureNotBlank(endpoint, "endpoint");

        this.endpointName = EndpointName.ofProjectLocationPublisherModelName(
            ensureNotBlank(project, "project"),
            ensureNotBlank(location, "location"),
            ensureNotBlank(publisher, "publisher"),
            ensureNotBlank(modelName, "modelName"));

        this.seed = seed;
        this.language = language;
        this.image = image;
        this.mask = mask;
        this.guidanceScale = guidanceScale;
        this.negativePrompt = negativePrompt;
        this.sampleImageStyle = sampleImageStyle;
        this.sampleImageSize = sampleImageSize;

        this.maxRetries = maxRetries == null ? 3 : maxRetries;

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
        try {
            PredictionServiceSettings serviceSettings = PredictionServiceSettings.newBuilder()
                .setEndpoint(this.endpoint)
                .build();

            try (PredictionServiceClient client = PredictionServiceClient.create(serviceSettings)) {

                // Instance description

                Map<String, Object> promptMap = new HashMap<>();
                promptMap.put("prompt", prompt);

                if (this.image != null && this.image.base64Data() != null) {
                    Map<String, String> imageMap = new HashMap<>();
                    imageMap.put("bytesBase64Encoded", this.image.base64Data());
                    promptMap.put("image", imageMap);
                }

                if (this.mask != null && this.mask.base64Data() != null) {
                    Map<String, String> imageMap = new HashMap<>();
                    imageMap.put("bytesBase64Encoded", this.mask.base64Data());

                    Map<String, Map<String, String>> maskMap = new HashMap<>();
                    maskMap.put("image", imageMap);

                    promptMap.put("mask", maskMap);
                }

                Value.Builder instanceBuilder = newBuilder();
                JsonFormat.parser().merge(toJson(promptMap), instanceBuilder);
                List<Value> instances = singletonList(instanceBuilder.build());

                // Parameters description

                Map<String, Object> paramsMap = new HashMap<>();

                paramsMap.put("sampleCount", n);

                if (this.seed != null) {
                    paramsMap.put("seed", this.seed);
                }

                if (this.sampleImageStyle != null) {
                    paramsMap.put("sampleImageStyle", this.sampleImageStyle.name());
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

                Value.Builder parametersBuilder = Value.newBuilder();
                JsonFormat.parser().merge(toJson(paramsMap), parametersBuilder);
                Value parameters = parametersBuilder.build();

                PredictResponse predictResponse =
                    withRetry(() -> client.predict(this.endpointName, instances, parameters), this.maxRetries);

                List<Image> allImages = predictResponse.getPredictionsList().stream()
                    .map(v -> {
                        String bytesBase64Encoded = v.getStructValue()
                            .getFieldsMap()
                            .get("bytesBase64Encoded")
                            .getStringValue();
                        return Image.builder()
                            .base64Data(bytesBase64Encoded)
                            .url(persistAndGetURI(bytesBase64Encoded))
                            .build();
                    })
                    .collect(Collectors.toList());

                return Response.from(allImages);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private URI persistAndGetURI(String bytesBase64Encoded) {
        if (this.withPersisting != null && this.withPersisting) {
            try {
                Path tempFile = Files.createTempFile(this.tempDirectory, "imagen-image-", ".png");
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
        private Image image;
        private Image mask;
        private String negativePrompt;
        private ImageStyle sampleImageStyle;
        private Integer sampleImageSize;
        private Integer maxRetries;
        private Integer guidanceScale;
        private Boolean withPersisting;
        private Path persistTo;

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder project(String project) {
            this.project = project;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder seed(Long seed) {
            this.seed = seed & 0xffffffffL;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder image(Image image) {
            this.image = image;
            return this;
        }

        public Builder image(Path path) {
            this.image = buildImageFromPath(path);
            return this;
        }

        public Builder mask(Image image) {
            this.mask = image;
            return this;
        }

        public Builder mask(Path path) {
            this.mask = buildImageFromPath(path);
            return this;
        }

        private Image buildImageFromPath(Path path) {
            try {
                byte[] allBytes = Files.readAllBytes(path);
                String base64 = Base64.getEncoder().encodeToString(allBytes);
                return Image.builder()
                    .url(path.toUri())
                    .base64Data(base64)
                    .build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Builder guidanceScale(Integer guidanceScale) {
            this.guidanceScale = guidanceScale;
            return this;
        }

        public Builder negativePrompt(String negativePrompt) {
            this.negativePrompt = negativePrompt;
            return this;
        }

        public Builder sampleImageStyle(ImageStyle sampleImageStyle) {
            this.sampleImageStyle = sampleImageStyle;
            return this;
        }

        public Builder sampleImageSize(Integer sampleImageSize) {
            this.sampleImageSize = sampleImageSize;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder withPersisting() {
            this.withPersisting = Boolean.TRUE;
            return this;
        }

        public Builder persistTo(Path persistTo) {
            this.persistTo = persistTo;
            return withPersisting();
        }

        public VertexAiImagenImageModel build() {
            return new VertexAiImagenImageModel(
                this.endpoint,
                this.project,
                this.location,
                this.publisher,
                this.modelName,
                this.seed,
                this.language,
                this.image,
                this.mask,
                this.guidanceScale,
                this.negativePrompt,
                this.sampleImageStyle,
                this.sampleImageSize,
                this.maxRetries,
                this.withPersisting,
                this.persistTo
            );
        }
    }
}
