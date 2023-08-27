package dev.langchain4j.model.vertex;

import com.google.cloud.aiplatform.util.ValueConverter;
import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.io.IOException;
import java.util.List;

import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents a connection to the Vertex embedding model, such as textembedding-gecko.
 */
public class VertexAiEmbeddingModel implements EmbeddingModel {

    private final String endpoint;
    private final String project;
    private final String location;
    private final String publisher;
    private final String modelName;
    private final Integer maxRetries;

    VertexAiEmbeddingModel(String endpoint,
                           String project,
                           String location,
                           String publisher,
                           String modelName,
                           Integer maxRetries) {

        this.endpoint = endpoint;
        this.project = project;
        this.location = location;
        this.publisher = publisher;
        this.modelName = modelName;
        this.maxRetries = maxRetries;
    }

    @Override
    public List<Embedding> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());

        return embedTexts(texts);
    }

    private List<Embedding> embedTexts(List<String> texts) {
        try {
            PredictionServiceSettings predictionServiceSettings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(endpoint)
                    .build();

            try (PredictionServiceClient client = PredictionServiceClient.create(predictionServiceSettings)) {
                EndpointName endpointName = EndpointName.ofProjectLocationPublisherModelName(project, location, publisher, modelName);

                Value.Builder instanceValue = Value.newBuilder();
                texts.forEach(content -> {
                    try {
                        JsonFormat.parser().merge(toJson(new VertexEmbeddingInstance(content)), instanceValue);
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });

                List<Value> instances = singletonList(instanceValue.build());

                PredictResponse response = withRetry(() -> client.predict(endpointName, instances, ValueConverter.EMPTY_VALUE), maxRetries);
                Embedding embedding = Embedding.from(extractContent(response));

                return singletonList(embedding);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Float> extractContent(PredictResponse predictResponse) {
        return predictResponse.getPredictions(0)
                .getStructValue()
                .getFieldsMap()
                .get("embeddings")
                .getStructValue()
                .getFieldsOrThrow("values")
                .getListValue()
                .getValuesList()
                .stream().map(v -> (float) v.getNumberValue())
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
