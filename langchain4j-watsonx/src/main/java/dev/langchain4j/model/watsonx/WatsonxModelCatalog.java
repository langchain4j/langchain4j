package dev.langchain4j.model.watsonx;

import static dev.langchain4j.model.ModelProvider.WATSONX;
import static java.util.Objects.nonNull;

import com.ibm.watsonx.ai.foundationmodel.FoundationModel.Function;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelParameters;
import com.ibm.watsonx.ai.foundationmodel.FoundationModelService;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.catalog.ModelType;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * A {@link ModelCatalog} implementation that integrates IBM watsonx.ai with LangChain4j.
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * ModelCatalog modelCatalog = WatsonxModelCatalog.builder()
 *     .baseUrl("https://...") // or use CloudRegion
 *     .build();
 * }</pre>
 *
 */
public class WatsonxModelCatalog implements ModelCatalog {
    private final FoundationModelService foundationModelService;
    private final FoundationModelParameters parameters;

    public WatsonxModelCatalog(Builder builder) {
        foundationModelService = FoundationModelService.builder()
                .baseUrl(builder.baseUrl)
                .version(builder.version)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .httpClient(builder.httpClient)
                .verifySsl(builder.verifySsl)
                .build();
        parameters =
                FoundationModelParameters.builder().limit(200).techPreview(true).build();
    }

    @Override
    public List<ModelDescription> listModels() {
        return foundationModelService.getModels(parameters).resources().stream()
                .map(model -> {
                    var builder = ModelDescription.builder()
                            .description(model.longDescription())
                            .displayName(model.label())
                            .name(model.modelId())
                            .owner(model.provider())
                            .provider(WATSONX)
                            .type(resolveModelType(model.functions()));

                    if (nonNull(model.lifecycle())) {
                        var createdAt = model.lifecycle().stream()
                                .filter(l -> l.id().equals("available"))
                                .findFirst()
                                .map(l -> LocalDate.parse(l.startDate())
                                        .atStartOfDay()
                                        .toInstant(ZoneOffset.UTC))
                                .orElse(null);
                        builder.createdAt(createdAt);
                    }

                    if (nonNull(model.modelLimits())) {
                        builder.maxInputTokens(model.modelLimits().maxSequenceLength())
                                .maxOutputTokens(model.modelLimits().maxOutputTokens());
                    }

                    return builder.build();
                })
                .toList();
    }

    @Override
    public ModelProvider provider() {
        return WATSONX;
    }

    /**
     * Returns a new {@link Builder} instance.
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * ModelCatalog modelCatalog = WatsonxModelCatalog.builder()
     *     .baseUrl("https://...") // or use CloudRegion
     *     .build();
     * }</pre>
     *
     * @return {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    private ModelType resolveModelType(List<Function> functions) {
        for (Function function : functions) {
            switch (function.id()) {
                case "text_chat", "image_chat" -> {
                    return ModelType.CHAT;
                }
                case "embedding" -> {
                    return ModelType.EMBEDDING;
                }
                case "rerank" -> {
                    return ModelType.SCORING;
                }
                default -> {}
            }
        }
        return ModelType.OTHER;
    }

    /**
     * Builder class for constructing {@link WatsonxModelCatalog} instances with configurable parameters.
     */
    public static class Builder extends WatsonxBuilder<Builder> {

        private Builder() {}

        public WatsonxModelCatalog build() {
            return new WatsonxModelCatalog(this);
        }
    }
}
