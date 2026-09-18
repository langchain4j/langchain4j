package dev.langchain4j.model.watsonx;

import static dev.langchain4j.model.ModelProvider.WATSONX;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.gateway.catalog.ModelGatewayCatalogService;
import com.ibm.watsonx.ai.gateway.catalog.ModelGatewayModel;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.catalog.ModelType;
import java.time.Instant;
import java.util.List;

/**
 * A {@link ModelCatalog} implementation that lists the models configured in the IBM watsonx.ai Model Gateway.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ModelCatalog modelCatalog = WatsonxGatewayModelCatalog.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .apiKey("...")
 *     .build();
 * }</pre>
 *
 */
public class WatsonxGatewayModelCatalog implements ModelCatalog {

    private final ModelGatewayCatalogService modelGatewayCatalogService;

    public WatsonxGatewayModelCatalog(Builder builder) {

        var serviceBuilder = nonNull(builder.authenticator)
                ? ModelGatewayCatalogService.builder().authenticator(builder.authenticator)
                : ModelGatewayCatalogService.builder().apiKey(builder.apiKey);

        modelGatewayCatalogService = serviceBuilder
                .baseUrl(builder.baseUrl)
                .version(builder.version)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
    }

    @Override
    public List<ModelDescription> listModels() {
        return modelGatewayCatalogService.listModels().stream()
                .map(WatsonxGatewayModelCatalog::toModelDescription)
                .toList();
    }

    @Override
    public ModelProvider provider() {
        return WATSONX;
    }

    private static ModelDescription toModelDescription(ModelGatewayModel model) {

        var id = nonNull(model.alias()) ? model.alias() : model.id();
        var builder = ModelDescription.builder()
                .name(id)
                .displayName(id)
                .description(model.description())
                .owner(model.ownedBy())
                .provider(WATSONX)
                .type(ModelType.CHAT);

        if (nonNull(model.created())) {
            builder.createdAt(Instant.ofEpochSecond(model.created()));
        }

        if (nonNull(model.metadata())) {
            builder.maxInputTokens(model.metadata().contextWindow());
        }

        return builder.build();
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * ModelCatalog modelCatalog = WatsonxGatewayModelCatalog.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .apiKey("...")
     *     .build();
     * }</pre>
     *
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing {@link WatsonxGatewayModelCatalog} instances with configurable parameters.
     */
    public static class Builder extends WatsonxConnectionBuilder<Builder> {

        private Builder() {}

        public WatsonxGatewayModelCatalog build() {
            return new WatsonxGatewayModelCatalog(this);
        }
    }
}
