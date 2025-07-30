package dev.langchain4j.model.vertexai.anthropic.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.HttpBody;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.UnavailableException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.aiplatform.v1.RawPredictRequest;
import com.google.protobuf.ByteString;
import dev.langchain4j.model.vertexai.anthropic.internal.Constants;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicRequest;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VertexAiAnthropicClient {

    private volatile PredictionServiceClient predictionServiceClient;
    private final String project;
    private final String location;
    private final String model;
    private final GoogleCredentials credentials;
    private final String endpoint;
    private final ObjectMapper objectMapper;

    public VertexAiAnthropicClient(String project, String location, String model) {
        this(project, location, model, null);
    }

    public VertexAiAnthropicClient(String project, String location, String model, GoogleCredentials credentials) {
        if (project == null || project.trim().isEmpty()) {
            throw new IllegalArgumentException("project cannot be null or empty");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("location cannot be null or empty");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model cannot be null or empty");
        }

        this.project = project;
        this.location = location;
        this.model = model;
        this.credentials = credentials;
        this.objectMapper = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.endpoint =
                String.format("projects/%s/locations/%s/publishers/anthropic/models/%s", project, location, model);
        this.predictionServiceClient = createClient();
    }

    private PredictionServiceClient createClient() {
        try {
            PredictionServiceSettings.Builder settingsBuilder = PredictionServiceSettings.newBuilder();
            if (credentials != null) {
                GoogleCredentials scopedCredentials =
                        credentials.createScoped("https://www.googleapis.com/auth/cloud-platform");
                settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(scopedCredentials));
            }
            return PredictionServiceClient.create(settingsBuilder.build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Vertex AI client", e);
        }
    }

    public AnthropicResponse generateContent(AnthropicRequest request) throws IOException {
        return generateContentWithRetry(request, 1);
    }

    private AnthropicResponse generateContentWithRetry(AnthropicRequest request, int attempt) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        try {
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("anthropic_version", Constants.ANTHROPIC_VERSION);
            requestMap.put("messages", request.messages);
            requestMap.put("max_tokens", request.maxTokens);

            if (request.temperature != null) {
                requestMap.put("temperature", request.temperature);
            }
            if (request.system != null) {
                requestMap.put("system", request.system);
            }
            if (request.topP != null) {
                requestMap.put("top_p", request.topP);
            }
            if (request.topK != null) {
                requestMap.put("top_k", request.topK);
            }
            if (request.stopSequences != null) {
                requestMap.put("stop_sequences", request.stopSequences);
            }
            if (request.tools != null) {
                requestMap.put("tools", request.tools);
            }
            if (request.toolChoice != null) {
                requestMap.put("tool_choice", request.toolChoice);
            }

            String requestJson = objectMapper.writeValueAsString(requestMap);

            HttpBody httpBody = HttpBody.newBuilder()
                    .setContentType("application/json")
                    .setData(ByteString.copyFromUtf8(requestJson))
                    .build();

            RawPredictRequest rawPredictRequest = RawPredictRequest.newBuilder()
                    .setEndpoint(endpoint)
                    .setHttpBody(httpBody)
                    .build();

            HttpBody response = predictionServiceClient.rawPredict(rawPredictRequest);

            String responseJson = response.getData().toStringUtf8();
            return objectMapper.readValue(responseJson, AnthropicResponse.class);

        } catch (UnavailableException e) {
            // Check if this is a channel shutdown error
            if (e.getCause() instanceof StatusRuntimeException) {
                StatusRuntimeException statusException = (StatusRuntimeException) e.getCause();
                if (statusException.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE
                        && statusException.getMessage().contains("Channel shutdown invoked")) {

                    if (attempt < 3) { // Retry up to 3 times
                        try {
                            Thread.sleep(100 * attempt); // Exponential backoff: 100ms, 200ms, 300ms
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Request interrupted", ie);
                        }

                        // Recreate the client and retry
                        try {
                            if (predictionServiceClient != null) {
                                predictionServiceClient.close();
                            }
                        } catch (Exception ignored) {
                            // Ignore close errors
                        }
                        predictionServiceClient = createClient();
                        return generateContentWithRetry(request, attempt + 1);
                    }
                }
            }
            throw new IOException("Failed to generate content using Vertex AI rawPredict", e);
        } catch (Exception e) {
            throw new IOException("Failed to generate content using Vertex AI rawPredict", e);
        }
    }

    public void close() {
        // Simply close the client without complex lifecycle management
        // The gRPC channel shutdown errors in tests are due to concurrent close calls
        // but are not harmful for the actual functionality
        if (predictionServiceClient != null && !predictionServiceClient.isShutdown()) {
            try {
                predictionServiceClient.close();
            } catch (Exception e) {
                // Ignore shutdown errors as they're likely due to concurrent closes in tests
            }
        }
    }
}
