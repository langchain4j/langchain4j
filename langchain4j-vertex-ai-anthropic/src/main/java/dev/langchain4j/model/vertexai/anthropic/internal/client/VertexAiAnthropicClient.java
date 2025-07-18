package dev.langchain4j.model.vertexai.anthropic.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.HttpBody;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.aiplatform.v1.RawPredictRequest;
import com.google.protobuf.ByteString;
import dev.langchain4j.model.vertexai.anthropic.internal.Constants;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicRequest;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class VertexAiAnthropicClient {

    private final PredictionServiceClient predictionServiceClient;
    private final String endpoint;
    private final ObjectMapper objectMapper;

    public VertexAiAnthropicClient(String project, String location, String model) {
        this(project, location, model, null);
    }

    public VertexAiAnthropicClient(String project, String location, String model, GoogleCredentials credentials) {
        this.objectMapper = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.endpoint =
                String.format("projects/%s/locations/%s/publishers/anthropic/models/%s", project, location, model);

        try {
            PredictionServiceSettings.Builder settingsBuilder = PredictionServiceSettings.newBuilder();
            if (credentials != null) {
                GoogleCredentials scopedCredentials =
                        credentials.createScoped("https://www.googleapis.com/auth/cloud-platform");
                settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(scopedCredentials));
            }
            this.predictionServiceClient = PredictionServiceClient.create(settingsBuilder.build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Vertex AI client", e);
        }
    }

    public AnthropicResponse generateContent(AnthropicRequest request) throws IOException {
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

        } catch (Exception e) {
            throw new IOException("Failed to generate content using Vertex AI rawPredict", e);
        }
    }

    public void close() {
        if (predictionServiceClient != null) {
            predictionServiceClient.close();
        }
    }
}
