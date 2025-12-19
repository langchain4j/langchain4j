package dev.langchain4j.model.bedrock;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelType;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.BedrockClientBuilder;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest;
import software.amazon.awssdk.services.bedrock.model.ModelModality;

/**
 * AWS Bedrock implementation of {@link ModelCatalog}.
 *
 * <p>Uses the AWS Bedrock API to dynamically discover available foundation models.
 *
 * <p>Example:
 * <pre>{@code
 * BedrockModelCatalog catalog = BedrockModelCatalog.builder()
 *     .region(Region.US_EAST_1)
 *     .build();
 *
 * List<ModelDescription> models = catalog.listModels();
 * }</pre>
 */
public class BedrockModelCatalog implements ModelCatalog {

    private final BedrockClient client;

    private BedrockModelCatalog(Builder builder) {
        AwsCredentialsProvider credentialsProvider =
                builder.credentialsProvider != null ? builder.credentialsProvider : DefaultCredentialsProvider.create();

        Region region = builder.region != null ? builder.region : Region.US_EAST_1;

        BedrockClientBuilder clientBuilder =
                BedrockClient.builder().credentialsProvider(credentialsProvider).region(region);

        if (builder.logger != null) {
            clientBuilder.overrideConfiguration(c -> c.addExecutionInterceptor(
                    new AwsLoggingInterceptor(builder.logRequests, builder.logResponses, builder.logger)));
        }

        this.client = clientBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> listModels() {
        ListFoundationModelsRequest.Builder requestBuilder = ListFoundationModelsRequest.builder();

        List<FoundationModelSummary> models =
                client.listFoundationModels(requestBuilder.build()).modelSummaries();

        List<ModelDescription> descriptions =
                models.stream().map(this::mapToModelDescription).collect(Collectors.toList());

        return descriptions;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.AMAZON_BEDROCK;
    }

    private ModelDescription mapToModelDescription(FoundationModelSummary modelSummary) {
        ModelDescription.Builder builder =
                ModelDescription.builder().name(modelSummary.modelId()).provider(ModelProvider.AMAZON_BEDROCK);

        // Use modelName if available, otherwise use modelId
        if (modelSummary.modelName() != null && !modelSummary.modelName().isEmpty()) {
            builder.displayName(modelSummary.modelName());
        } else {
            builder.displayName(modelSummary.modelId());
        }

        // Set provider name as owner
        if (modelSummary.providerName() != null) {
            builder.owner(modelSummary.providerName());
        }

        // Determine model type from output modalities
        if (modelSummary.hasOutputModalities()) {
            List<ModelModality> outputModalities = modelSummary.outputModalities();
            if (outputModalities.contains(ModelModality.TEXT)) {
                builder.type(ModelType.CHAT);
            } else if (outputModalities.contains(ModelModality.EMBEDDING)) {
                builder.type(ModelType.EMBEDDING);
            } else if (outputModalities.contains(ModelModality.IMAGE)) {
                builder.type(ModelType.IMAGE_GENERATION);
            }
        }

        // Check for deprecated status via lifecycle
        if (modelSummary.modelLifecycle() != null
                && modelSummary.modelLifecycle().status() != null) {
            String status = modelSummary.modelLifecycle().status().toString();
            boolean isDeprecated = "LEGACY".equalsIgnoreCase(status);
            builder.deprecated(isDeprecated);
        }

        return builder.build();
    }

    public static class Builder {
        private Region region;
        private AwsCredentialsProvider credentialsProvider;
        private boolean logRequests;
        private boolean logResponses;
        private Logger logger;

        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        public Builder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
            this.credentialsProvider = credentialsProvider;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public BedrockModelCatalog build() {
            return new BedrockModelCatalog(this);
        }
    }
}
