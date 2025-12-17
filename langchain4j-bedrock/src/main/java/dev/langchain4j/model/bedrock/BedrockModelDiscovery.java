package dev.langchain4j.model.bedrock;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscovery;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.discovery.ModelType;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.BedrockClientBuilder;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest;
import software.amazon.awssdk.services.bedrock.model.ModelModality;

/**
 * AWS Bedrock implementation of {@link ModelDiscovery}.
 *
 * <p>Uses the AWS Bedrock API to dynamically discover available foundation models.
 *
 * <p>Example:
 * <pre>{@code
 * BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
 *     .region(Region.US_EAST_1)
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 * }</pre>
 */
public class BedrockModelDiscovery implements ModelDiscovery {

    private final BedrockClient client;

    private BedrockModelDiscovery(Builder builder) {
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
    public List<ModelDescription> discoverModels() {
        return discoverModels(null);
    }

    @Override
    public List<ModelDescription> discoverModels(ModelDiscoveryFilter filter) {
        ListFoundationModelsRequest.Builder requestBuilder = ListFoundationModelsRequest.builder();

        // Apply server-side filters if available
        if (filter != null && !filter.matchesAll()) {
            applyServerSideFilter(requestBuilder, filter);
        }

        List<FoundationModelSummary> models =
                client.listFoundationModels(requestBuilder.build()).modelSummaries();

        List<ModelDescription> descriptions =
                models.stream().map(this::mapToModelDescription).collect(Collectors.toList());

        // Apply client-side filters for unsupported filter criteria
        if (filter != null && !filter.matchesAll()) {
            return filterModels(descriptions, filter);
        }

        return descriptions;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.AMAZON_BEDROCK;
    }

    @Override
    public boolean supportsFiltering() {
        return true; // Bedrock supports some server-side filtering
    }

    private void applyServerSideFilter(
            ListFoundationModelsRequest.Builder requestBuilder, ModelDiscoveryFilter filter) {
        // Filter by output modality (maps to type)
        if (filter.getTypes() != null && !filter.getTypes().isEmpty()) {
            if (filter.getTypes().size() == 1) {
                ModelType type = filter.getTypes().iterator().next();
                if (type == ModelType.CHAT) {
                    requestBuilder.byOutputModality(ModelModality.TEXT);
                } else if (type == ModelType.EMBEDDING) {
                    requestBuilder.byOutputModality(ModelModality.EMBEDDING);
                } else if (type == ModelType.IMAGE_GENERATION) {
                    requestBuilder.byOutputModality(ModelModality.IMAGE);
                }
            }
        }
    }

    private ModelDescription mapToModelDescription(FoundationModelSummary modelSummary) {
        ModelDescription.Builder builder =
                ModelDescription.builder().id(modelSummary.modelId()).provider(ModelProvider.AMAZON_BEDROCK);

        // Use modelName if available, otherwise use modelId
        if (modelSummary.modelName() != null && !modelSummary.modelName().isEmpty()) {
            builder.name(modelSummary.modelName());
        } else {
            builder.name(modelSummary.modelId());
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

    private List<ModelDescription> filterModels(List<ModelDescription> models, ModelDiscoveryFilter filter) {
        return models.stream().filter(model -> matchesFilter(model, filter)).collect(Collectors.toList());
    }

    private boolean matchesFilter(ModelDescription model, ModelDiscoveryFilter filter) {
        // Filter by type (already applied server-side, but check for completeness)
        if (filter.getTypes() != null && !filter.getTypes().isEmpty()) {
            if (model.getType() == null || !filter.getTypes().contains(model.getType())) {
                return false;
            }
        }

        // Filter by required capabilities
        if (filter.getRequiredCapabilities() != null
                && !filter.getRequiredCapabilities().isEmpty()) {
            if (model.getCapabilities() == null
                    || !model.getCapabilities().containsAll(filter.getRequiredCapabilities())) {
                return false;
            }
        }

        // Filter by minimum context window
        if (filter.getMinContextWindow() != null) {
            if (model.getContextWindow() == null || model.getContextWindow() < filter.getMinContextWindow()) {
                return false;
            }
        }

        // Filter by maximum context window
        if (filter.getMaxContextWindow() != null) {
            if (model.getContextWindow() == null || model.getContextWindow() > filter.getMaxContextWindow()) {
                return false;
            }
        }

        // Filter by name pattern
        if (filter.getNamePattern() != null) {
            Pattern pattern = Pattern.compile(filter.getNamePattern());
            if (!pattern.matcher(model.getName()).matches()) {
                return false;
            }
        }

        // Filter by deprecated status
        if (filter.getIncludeDeprecated() != null && !filter.getIncludeDeprecated()) {
            if (Boolean.TRUE.equals(model.isDeprecated())) {
                return false;
            }
        }

        return true;
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

        public BedrockModelDiscovery build() {
            return new BedrockModelDiscovery(this);
        }
    }
}
