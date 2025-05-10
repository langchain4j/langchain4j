package dev.langchain4j.model.vertexai;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.NoCredentials;
import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.LlmUtilityServiceClient;
import com.google.cloud.vertexai.api.LlmUtilityServiceSettings;
import com.google.cloud.vertexai.api.PredictionServiceClient;
import com.google.cloud.vertexai.api.PredictionServiceSettings;
import com.google.cloud.vertexai.api.stub.LlmUtilityServiceStubSettings;
import java.io.IOException;
import java.time.Duration;

public class VertexAiFactory {
    private VertexAiFactory() {}

    @SuppressWarnings("resource")
    public static VertexAI createTestVertexAI(
            String endpoint, final String projectId, final String location, Duration timeout) {
        try {
            final var channelProvider = LlmUtilityServiceStubSettings.defaultHttpJsonTransportProviderBuilder()
                    .setEndpoint(endpoint)
                    .build();

            final var newHttpJsonBuilder = LlmUtilityServiceStubSettings.newHttpJsonBuilder();
            newHttpJsonBuilder
                    .unaryMethodSettingsBuilders()
                    .forEach(builder -> builder.setSimpleTimeoutNoRetriesDuration(timeout));

            final var llmUtilityServiceStubSettings = newHttpJsonBuilder
                    .setEndpoint(endpoint)
                    .setCredentialsProvider(NoCredentialsProvider.create())
                    .setTransportChannelProvider(channelProvider)
                    .build();

            final var llmUtilityServiceClient =
                    LlmUtilityServiceClient.create(LlmUtilityServiceSettings.create(llmUtilityServiceStubSettings));

            final var predictionServiceSettingsBuilder = PredictionServiceSettings.newHttpJsonBuilder()
                    .setEndpoint(endpoint)
                    .setCredentialsProvider(NoCredentialsProvider.create())
                    .applyToAllUnaryMethods(updater -> {
                        updater.setSimpleTimeoutNoRetriesDuration(timeout);
                        return null;
                    });
            final var predictionServiceSettings = predictionServiceSettingsBuilder.build();
            final var predictionClient = PredictionServiceClient.create(predictionServiceSettings);

            return new VertexAI.Builder()
                    .setTransport(Transport.REST)
                    .setProjectId(projectId)
                    .setLocation(location)
                    .setLlmClientSupplier(() -> llmUtilityServiceClient)
                    .setPredictionClientSupplier(() -> predictionClient)
                    .setCredentials(NoCredentials.getInstance())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
