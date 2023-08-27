package dev.langchain4j.model.vertex;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.model.language.LanguageModel;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.google.protobuf.Value.newBuilder;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents a connection to the Vertex AI LLM with a text interface, such as text-bison.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/text/text-overview">here</a>.
 */
public class VextexAiLanguageModel implements LanguageModel {

    private final String endpoint;
    private final String project;
    private final String location;
    private final String publisher;
    private final String modelName;
    private final VertexAiParameters vertexAiParameters;
    private final Integer maxRetries;

    public VextexAiLanguageModel(String endpoint,
                                 String project,
                                 String location,
                                 String publisher,
                                 String modelName,
                                 Double temperature,
                                 Integer maxOutputTokens,
                                 Integer topK,
                                 Double topP,
                                 Integer maxRetries) {
        this.endpoint = ensureNotBlank(endpoint, "endpoint");
        this.project = ensureNotBlank(project, "project");
        this.location = ensureNotBlank(location, "location");
        this.publisher = ensureNotBlank(publisher, "publisher");
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.vertexAiParameters = new VertexAiParameters(temperature, maxOutputTokens, topK, topP);
        this.maxRetries = maxRetries == null ? 3 : maxRetries;
    }

    @Override
    public String process(String text) {

        try {
            PredictionServiceSettings predictionServiceSettings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(endpoint)
                    .build();

            PredictionServiceClient client = PredictionServiceClient.create(predictionServiceSettings);

            final EndpointName endpointName = EndpointName.ofProjectLocationPublisherModelName(project, location, publisher, modelName);

            VertexAiTextInstance vertexAiTextInstance = new VertexAiTextInstance(text);

            Value.Builder instanceBuilder = newBuilder();
            JsonFormat.parser().merge(toJson(vertexAiTextInstance), instanceBuilder);
            List<Value> instances = Collections.singletonList(instanceBuilder.build());

            Value.Builder parametersBuilder = Value.newBuilder();
            JsonFormat.parser().merge(toJson(vertexAiParameters), parametersBuilder);
            Value parameters = parametersBuilder.build();

            PredictResponse response = withRetry(() -> client.predict(endpointName, instances, parameters), maxRetries);

            return extractContent(response);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractContent(PredictResponse predictResponse) {
        return predictResponse.getPredictions(0)
                .getStructValue()
                .getFieldsMap()
                .get("content")
                .getStringValue();
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

        private Double temperature;
        private Integer maxOutputTokens = 200;
        private Integer topK;
        private Double topP;

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

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public VextexAiLanguageModel build() {
            return new VextexAiLanguageModel(
                    endpoint,
                    project,
                    location,
                    publisher,
                    modelName,
                    temperature,
                    maxOutputTokens,
                    topK,
                    topP,
                    maxRetries);
        }
    }
}
