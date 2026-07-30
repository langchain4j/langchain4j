package dev.langchain4j.model.vertexai.anthropic.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.HttpBody;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.UnavailableException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.EndpointName;
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
import java.util.Locale;
import java.util.Map;

public class VertexAiAnthropicClient {

    private static final String PUBLISHER = "anthropic";

    private static final String GLOBAL_ENDPOINT = "aiplatform.googleapis.com:443";
    private static final String MULTI_REGION_ENDPOINT_TEMPLATE = "aiplatform.%s.rep.googleapis.com:443";
    private static final String REGIONAL_ENDPOINT_TEMPLATE = "%s-aiplatform.googleapis.com:443";

    private volatile PredictionServiceClient predictionServiceClient;
    private final String project;
    private final String location;
    private final GoogleCredentials credentials;
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
        // Note: model parameter is kept for backward compatibility but is now ignored
        // The actual model is determined per request
        // Suppress SonarQube warning about unused parameter - kept for API compatibility
        @SuppressWarnings("unused")
        String ignoredModel = model;

        this.project = project;
        // Vertex AI locations are lowercase, both in the endpoint host and in the resource name
        this.location = location.toLowerCase(Locale.ROOT);
        this.credentials = credentials;
        this.objectMapper = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.predictionServiceClient = createClient();
    }

    private PredictionServiceClient createClient() {
        try {
            PredictionServiceSettings.Builder settingsBuilder = PredictionServiceSettings.newBuilder();
            settingsBuilder.setEndpoint(resolveEndpoint(location));
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

    /**
     * Maps a Vertex AI location to the API endpoint host serving it. Vertex AI uses a different host
     * format for each of its three location types:
     * <ul>
     *     <li>{@code global} &rarr; {@code aiplatform.googleapis.com}</li>
     *     <li>multi-region ({@code us}, {@code eu}) &rarr; {@code aiplatform.<location>.rep.googleapis.com}</li>
     *     <li>region (e.g. {@code us-east5}) &rarr; {@code <location>-aiplatform.googleapis.com}</li>
     * </ul>
     * See <a href="https://platform.claude.com/docs/en/build-with-claude/claude-on-vertex-ai">Claude on Vertex AI</a>.
     *
     * @param location the Vertex AI location, case-insensitive
     * @return the {@code host:port} endpoint to use for {@link PredictionServiceSettings}
     */
    public static String resolveEndpoint(String location) {
        String normalized = location.toLowerCase(Locale.ROOT);
        if ("global".equals(normalized)) {
            return GLOBAL_ENDPOINT;
        }
        if ("us".equals(normalized) || "eu".equals(normalized)) {
            return String.format(MULTI_REGION_ENDPOINT_TEMPLATE, normalized);
        }
        return String.format(REGIONAL_ENDPOINT_TEMPLATE, normalized);
    }

    public AnthropicResponse generateContent(AnthropicRequest request, String modelName) throws IOException {
        return generateContentWithRetry(request, modelName, 1);
    }

    /**
     * Streaming version of generateContent - simulates streaming by chunking the response
     */
    public void generateContentStreaming(AnthropicRequest request, String modelName, StreamingResponseHandler handler)
            throws IOException {
        try {
            // For now, use the synchronous method and simulate streaming
            AnthropicResponse response = generateContent(request, modelName);

            // Send response metadata first
            handler.onResponse(response);

            // Simulate streaming by sending the response in chunks
            if (response.content != null && !response.content.isEmpty()) {
                processContentForStreaming(response.content, handler);
            }

            handler.onComplete();

        } catch (Exception e) {
            handler.onError(e);
        }
    }

    private void processContentForStreaming(
            java.util.List<dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicContent> contentList,
            StreamingResponseHandler handler) {
        for (dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicContent content : contentList) {
            if (Constants.TEXT_CONTENT_TYPE.equals(content.type) && content.text != null) {
                processTextContentStreaming(content.text, handler);
            } else if (Constants.TOOL_USE_CONTENT_TYPE.equals(content.type)) {
                processToolContentStreaming(content, handler);
            }
        }
    }

    private void processTextContentStreaming(String text, StreamingResponseHandler handler) {
        int chunkSize = 10; // Small chunks to simulate streaming

        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end);

            // Create a streaming-like JSON chunk
            String streamChunk = String.format(
                    "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"%s\"}}",
                    chunk.replace("\"", "\\\"").replace("\n", "\\n"));

            handler.onChunk(streamChunk);

            // Small delay to simulate streaming
            simulateStreamingDelay(handler);
        }
    }

    private void processToolContentStreaming(
            dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicContent content,
            StreamingResponseHandler handler) {
        String toolChunk = String.format(
                "{\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\",\"id\":\"%s\",\"name\":\"%s\"}}",
                content.id, content.name);
        handler.onChunk(toolChunk);
    }

    private void simulateStreamingDelay(StreamingResponseHandler handler) {
        try {
            Thread.sleep(10); // NOSONAR - Intentional for streaming simulation
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handler.onError(e);
        }
    }

    private AnthropicResponse generateContentWithRetry(AnthropicRequest request, String modelName, int attempt)
            throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        try {
            Map<String, Object> requestMap = buildRequestMap(request);
            String requestJson = objectMapper.writeValueAsString(requestMap);

            RawPredictRequest rawPredictRequest = buildRawPredictRequest(requestJson, modelName);
            HttpBody response = predictionServiceClient.rawPredict(rawPredictRequest);

            String responseJson = response.getData().toStringUtf8();
            return objectMapper.readValue(responseJson, AnthropicResponse.class);

        } catch (UnavailableException e) {
            return handleUnavailableException(e, request, modelName, attempt);
        } catch (Exception e) {
            throw new IOException("Failed to generate content using Vertex AI rawPredict", e);
        }
    }

    private Map<String, Object> buildRequestMap(AnthropicRequest request) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("anthropic_version", Constants.ANTHROPIC_VERSION);
        requestMap.put("messages", request.messages);
        requestMap.put("max_tokens", request.maxTokens);

        addOptionalParameter(requestMap, "temperature", request.temperature);
        addOptionalParameter(requestMap, "system", request.system);
        addOptionalParameter(requestMap, "top_p", request.topP);
        addOptionalParameter(requestMap, "top_k", request.topK);
        addOptionalParameter(requestMap, "stop_sequences", request.stopSequences);
        addOptionalParameter(requestMap, "tools", request.tools);
        addOptionalParameter(requestMap, "tool_choice", request.toolChoice);

        return requestMap;
    }

    private void addOptionalParameter(Map<String, Object> requestMap, String key, Object value) {
        if (value != null) {
            requestMap.put(key, value);
        }
    }

    private RawPredictRequest buildRawPredictRequest(String requestJson, String modelName) {
        String endpoint = EndpointName.ofProjectLocationPublisherModelName(project, location, PUBLISHER, modelName)
                .toString();

        HttpBody httpBody = HttpBody.newBuilder()
                .setContentType("application/json")
                .setData(ByteString.copyFromUtf8(requestJson))
                .build();

        return RawPredictRequest.newBuilder()
                .setEndpoint(endpoint)
                .setHttpBody(httpBody)
                .build();
    }

    private AnthropicResponse handleUnavailableException(
            UnavailableException e, AnthropicRequest request, String modelName, int attempt) throws IOException {
        if (isChannelShutdownError(e) && attempt < 3) {
            return retryAfterDelay(request, modelName, attempt);
        }
        throw new IOException("Failed to generate content using Vertex AI rawPredict", e);
    }

    private boolean isChannelShutdownError(UnavailableException e) {
        return e.getCause() instanceof StatusRuntimeException statusException
                && statusException.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE
                && statusException.getMessage().contains("Channel shutdown invoked");
    }

    private AnthropicResponse retryAfterDelay(AnthropicRequest request, String modelName, int attempt)
            throws IOException {
        try {
            Thread.sleep(100L * attempt); // NOSONAR - Intentional for retry backoff
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", ie);
        }

        recreateClient();
        return generateContentWithRetry(request, modelName, attempt + 1);
    }

    private void recreateClient() {
        try {
            if (predictionServiceClient != null) {
                predictionServiceClient.close();
            }
        } catch (Exception ignored) { // NOSONAR - Intentionally catching all exceptions during cleanup
            // Ignore close errors during retry cleanup
        }
        predictionServiceClient = createClient();
    }

    public void close() {
        // Simply close the client without complex lifecycle management
        // The gRPC channel shutdown errors in tests are due to concurrent close calls
        // but are not harmful for the actual functionality
        if (predictionServiceClient != null && !predictionServiceClient.isShutdown()) {
            try {
                predictionServiceClient.close();
            } catch (Exception e) { // NOSONAR - Intentionally catching all exceptions during shutdown
                // Ignore shutdown errors as they're likely due to concurrent closes in tests
            }
        }
    }
}
