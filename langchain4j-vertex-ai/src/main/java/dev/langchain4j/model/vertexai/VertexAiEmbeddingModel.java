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

import java.io.IOException;
import java.util.List;

import static com.google.cloud.aiplatform.util.ValueConverter.EMPTY_VALUE;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents a connection to the Vertex AI embedding model, such as textembedding-gecko.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/embeddings/get-text-embeddings">here</a>.
 */
public class VertexAiEmbeddingModel implements EmbeddingModel {

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
        this.maxRetries = maxRetries == null ? 3 : maxRetries;
    }

    @Override
    public List<Embedding> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private List<Embedding> embedTexts(List<String> texts) {

        try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {

            Value.Builder instanceBuilder = Value.newBuilder();
            for (String text : texts) {
                JsonFormat.parser().merge(toJson(new VertexAiEmbeddingInstance(text)), instanceBuilder);
            }
            List<Value> instances = singletonList(instanceBuilder.build());

            PredictResponse response = withRetry(() -> client.predict(endpointName, instances, EMPTY_VALUE), maxRetries);

            Embedding embedding = Embedding.from(toVector(response));
            return singletonList(embedding);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Float> toVector(PredictResponse predictResponse) {
        return predictResponse.getPredictions(0)
                .getStructValue()
                .getFieldsMap()
                .get("embeddings")
                .getStructValue()
                .getFieldsOrThrow("values")
                .getListValue()
                .getValuesList()
                .stream()
                .map(v -> (float) v.getNumberValue())
                .collect(toList());
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
