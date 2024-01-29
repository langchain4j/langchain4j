package dev.langchain4j.model.vertexai;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.vertexai.spi.VertexAiEmbeddingModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.cloud.aiplatform.util.ValueConverter.EMPTY_VALUE;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.stream.Collectors.toList;

/**
 * Represents a Google Vertex AI embedding model, such as textembedding-gecko.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/embeddings/get-text-embeddings">here</a>.
 * <br>
 * Please follow these steps before using this model:
 * <br>
 * 1. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#authentication">Authentication</a>
 * <br>
 * When developing locally, you can use one of:
 * <br>
 * a) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#local-developmenttesting">Google Cloud SDK</a>
 * <br>
 * b) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#using-a-service-account-recommended">Service account</a>
 * When using service account, ensure that <code>GOOGLE_APPLICATION_CREDENTIALS</code> environment variable points to your JSON service account key.
 * <br>
 * 2. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#authorization">Authorization</a>
 * <br>
 * 3. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#prerequisites">Prerequisites</a>
 */
public class VertexAiEmbeddingModel implements EmbeddingModel {

    private static final int BATCH_SIZE = 250; // Vertex AI has a limit of up to 250 input texts per request

    private final PredictionServiceSettings settings;
    private final EndpointName endpointName;
    private final Integer maxRetries;

    public VertexAiEmbeddingModel(String endpoint,
                                  String project,
                                  String location,
                                  String publisher,
                                  String modelName,
                                  Integer maxRetries) {
        try {
            this.settings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(ensureNotBlank(endpoint, "endpoint"))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.endpointName = EndpointName.ofProjectLocationPublisherModelName(
                ensureNotBlank(project, "project"),
                ensureNotBlank(location, "location"),
                ensureNotBlank(publisher, "publisher"),
                ensureNotBlank(modelName, "modelName")
        );
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {

        try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {

            List<Embedding> embeddings = new ArrayList<>();
            int inputTokenCount = 0;

            for (int i = 0; i < segments.size(); i += BATCH_SIZE) {

                List<TextSegment> batch = segments.subList(i, Math.min(i + BATCH_SIZE, segments.size()));

                List<Value> instances = new ArrayList<>();
                for (TextSegment segment : batch) {
                    Value.Builder instanceBuilder = Value.newBuilder();
                    JsonFormat.parser().merge(toJson(new VertexAiEmbeddingInstance(segment.text())), instanceBuilder);
                    instances.add(instanceBuilder.build());
                }

                PredictResponse response = withRetry(() -> client.predict(endpointName, instances, EMPTY_VALUE), maxRetries);

                embeddings.addAll(response.getPredictionsList().stream()
                        .map(VertexAiEmbeddingModel::toEmbedding)
                        .collect(toList()));

                for (Value prediction : response.getPredictionsList()) {
                    inputTokenCount += extractTokenCount(prediction);
                }
            }

            return Response.from(
                    embeddings,
                    new TokenUsage(inputTokenCount)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Embedding toEmbedding(Value prediction) {

        List<Float> vector = prediction.getStructValue()
                .getFieldsMap()
                .get("embeddings")
                .getStructValue()
                .getFieldsOrThrow("values")
                .getListValue()
                .getValuesList()
                .stream()
                .map(v -> (float) v.getNumberValue())
                .collect(toList());

        return Embedding.from(vector);
    }

    private static int extractTokenCount(Value prediction) {
        return (int) prediction.getStructValue()
                .getFieldsMap()
                .get("embeddings")
                .getStructValue()
                .getFieldsMap()
                .get("statistics")
                .getStructValue()
                .getFieldsMap()
                .get("token_count")
                .getNumberValue();
    }

    public static Builder builder() {
        return ServiceHelper.loadFactoryService(
                VertexAiEmbeddingModelBuilderFactory.class,
                Builder::new
        );
    }

    public static class Builder {

        private String endpoint;
        private String project;
        private String location;
        private String publisher;
        private String modelName;
        private Integer maxRetries;

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

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public VertexAiEmbeddingModel build() {
            return new VertexAiEmbeddingModel(
                    endpoint,
                    project,
                    location,
                    publisher,
                    modelName,
                    maxRetries);
        }
    }
}
