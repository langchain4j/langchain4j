package dev.langchain4j.model.vertexai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Base64.getEncoder;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.ImageEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Multimodal embedding model using Google Vertex AI's multimodalembedding@001 model.
 * <p>
 * Implements both {@link EmbeddingModel} and {@link ImageEmbeddingModel} to support
 * embedding text and images into the same vector space for cross-modal search.
 * <p>
 * <b>Supported inputs:</b>
 * <ul>
 *   <li>Text: Up to 32 tokens (English only)</li>
 *   <li>Images: BMP, GIF, JPG, PNG (max 20MB)</li>
 * </ul>
 * <p>
 * <b>Output dimensions:</b> 128, 256, 512, or 1408 (default)
 * <p>
 * Example usage:
 * <pre>{@code
 * VertexAiMultimodalEmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
 *         .project("my-project")
 *         .location("us-central1")
 *         .outputDimension(256)
 *         .build();
 *
 * // Embed text (via EmbeddingModel interface)
 * Embedding textEmbedding = model.embed("a photo of a cat").content();
 *
 * // Embed image (via ImageEmbeddingModel interface)
 * ImageContent image = ImageContent.from(base64Data, "image/jpeg");
 * Embedding imageEmbedding = model.embed(image).content();
 *
 * // Compare similarity
 * double similarity = CosineSimilarity.between(textEmbedding, imageEmbedding);
 * }</pre>
 *
 * @see <a href="https://cloud.google.com/vertex-ai/generative-ai/docs/embeddings/get-multimodal-embeddings">Vertex AI Multimodal Embeddings</a>
 */
public class VertexAiMultimodalEmbeddingModel implements EmbeddingModel, ImageEmbeddingModel {

    private static final String DEFAULT_GOOGLEAPIS_ENDPOINT_SUFFIX = "-aiplatform.googleapis.com:443";
    private static final String DEFAULT_PUBLISHER = "google";
    private static final String DEFAULT_MODEL_NAME = "multimodalembedding@001";
    private static final int DEFAULT_DIMENSION = 1408;

    private final PredictionServiceSettings settings;
    private final String endpointName;
    private final Integer outputDimension;
    private final Integer maxRetries;
    private final String modelName;

    // Cached dimension
    private Integer cachedDimension;

    private VertexAiMultimodalEmbeddingModel(Builder builder) {
        String regionWithBaseAPI = builder.endpoint != null
                ? builder.endpoint
                : ensureNotBlank(builder.location, "location") + DEFAULT_GOOGLEAPIS_ENDPOINT_SUFFIX;

        this.endpointName = String.format(
                "projects/%s/locations/%s/publishers/%s/models/%s",
                ensureNotBlank(builder.project, "project"),
                ensureNotBlank(builder.location, "location"),
                getOrDefault(builder.publisher, DEFAULT_PUBLISHER),
                getOrDefault(builder.modelName, DEFAULT_MODEL_NAME));

        this.outputDimension = getOrDefault(builder.outputDimension, DEFAULT_DIMENSION);
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.modelName = getOrDefault(builder.modelName, DEFAULT_MODEL_NAME);
        this.cachedDimension = this.outputDimension;

        try {
            PredictionServiceSettings.Builder settingsBuilder =
                    PredictionServiceSettings.newBuilder().setEndpoint(regionWithBaseAPI);
            if (builder.credentials != null) {
                GoogleCredentials scopedCredentials =
                        builder.credentials.createScoped("https://www.googleapis.com/auth/cloud-platform");
                settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(scopedCredentials));
            }
            this.settings = settingsBuilder.build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create PredictionServiceSettings", e);
        }
    }

    // ==================== EmbeddingModel Implementation ====================

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        ensureNotNull(textSegments, "textSegments");
        if (textSegments.isEmpty()) {
            return Response.from(List.of());
        }

        try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {
            List<Embedding> embeddings = new ArrayList<>();

            for (TextSegment segment : textSegments) {
                Value instance = toTextInstance(segment.text());
                Value parameters = toParameters();

                PredictResponse response = withRetryMappingExceptions(
                        () -> client.predict(endpointName, List.of(instance), parameters), maxRetries);

                Embedding embedding = extractTextEmbedding(response);
                embeddings.add(embedding);
            }

            return Response.from(embeddings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create PredictionServiceClient", e);
        }
    }

    // ==================== ImageEmbeddingModel Implementation ====================

    @Override
    public Response<List<Embedding>> embedAllImages(List<ImageContent> images) {
        ensureNotNull(images, "images");
        if (images.isEmpty()) {
            return Response.from(List.of());
        }

        try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {
            List<Embedding> embeddings = new ArrayList<>();

            for (ImageContent imageContent : images) {
                Value instance = toImageInstance(imageContent);
                Value parameters = toParameters();

                PredictResponse response = withRetryMappingExceptions(
                        () -> client.predict(endpointName, List.of(instance), parameters), maxRetries);

                Embedding embedding = extractImageEmbedding(response);
                embeddings.add(embedding);
            }

            return Response.from(embeddings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create PredictionServiceClient", e);
        }
    }

    // ==================== Shared Methods ====================

    @Override
    public int dimension() {
        if (cachedDimension != null) {
            return cachedDimension;
        }
        // Fallback: make a test embedding
        cachedDimension = embed("test").content().dimension();
        return cachedDimension;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    // ==================== Private Helper Methods ====================

    private Value toTextInstance(String text) {
        try {
            String json = String.format("{\"text\": \"%s\"}", escapeJson(text));
            Value.Builder builder = Value.newBuilder();
            JsonFormat.parser().merge(json, builder);
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build text instance", e);
        }
    }

    private Value toImageInstance(ImageContent imageContent) {
        try {
            String json = toImageInstanceJson(imageContent);
            Value.Builder builder = Value.newBuilder();
            JsonFormat.parser().merge(json, builder);
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build image instance", e);
        }
    }

    private String toImageInstanceJson(ImageContent imageContent) {
        Image image = imageContent.image();

        if (image.url() != null) {
            URI uri = image.url();
            if ("gs".equals(uri.getScheme())) {
                // GCS URI
                return String.format("{\"image\": {\"gcsUri\": \"%s\"}}", uri);
            } else {
                // HTTP(S) URL - download and encode as base64
                byte[] bytes = readBytes(uri.toString());
                String base64 = getEncoder().encodeToString(bytes);
                String mimeType = getOrDefault(image.mimeType(), "image/png");
                return String.format(
                        "{\"image\": {\"bytesBase64Encoded\": \"%s\", \"mimeType\": \"%s\"}}", base64, mimeType);
            }
        } else if (image.base64Data() != null) {
            String mimeType = getOrDefault(image.mimeType(), "image/png");
            return String.format(
                    "{\"image\": {\"bytesBase64Encoded\": \"%s\", \"mimeType\": \"%s\"}}",
                    image.base64Data(), mimeType);
        } else {
            throw new IllegalArgumentException("ImageContent must have either URL or base64Data");
        }
    }

    private Value toParameters() {
        try {
            String json = String.format("{\"dimension\": %d}", outputDimension);
            Value.Builder builder = Value.newBuilder();
            JsonFormat.parser().merge(json, builder);
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build parameters", e);
        }
    }

    private Embedding extractTextEmbedding(PredictResponse response) {
        return extractEmbedding(response, "textEmbedding");
    }

    private Embedding extractImageEmbedding(PredictResponse response) {
        return extractEmbedding(response, "imageEmbedding");
    }

    private Embedding extractEmbedding(PredictResponse response, String embeddingKey) {
        if (response.getPredictionsList().isEmpty()) {
            throw new RuntimeException("No predictions returned from Vertex AI");
        }

        Value prediction = response.getPredictions(0);

        List<Float> vector =
                prediction.getStructValue().getFieldsMap().get(embeddingKey).getListValue().getValuesList().stream()
                        .map(v -> (float) v.getNumberValue())
                        .toList();

        return Embedding.from(vector);
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String endpoint;
        private String project;
        private String location;
        private String publisher;
        private String modelName;
        private Integer outputDimension;
        private Integer maxRetries;
        private GoogleCredentials credentials;

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

        public Builder outputDimension(Integer outputDimension) {
            this.outputDimension = outputDimension;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public VertexAiMultimodalEmbeddingModel build() {
            return new VertexAiMultimodalEmbeddingModel(this);
        }
    }
}
