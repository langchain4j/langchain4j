package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscovery;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.discovery.ModelPricing;
import dev.langchain4j.model.discovery.ModelType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Anthropic implementation of {@link ModelDiscovery}.
 *
 * <p>Since Anthropic does not provide a model listing API endpoint,
 * this implementation returns a curated list of known Anthropic models
 * based on their public documentation.
 *
 * <p>Note: This list may not be exhaustive and is updated periodically.
 * For the most current model information, please refer to Anthropic's
 * official documentation at https://docs.anthropic.com/
 *
 * <p>Example:
 * <pre>{@code
 * AnthropicModelDiscovery discovery = AnthropicModelDiscovery.builder().build();
 * List<ModelDescription> models = discovery.discoverModels();
 * }</pre>
 */
public class AnthropicModelDiscovery implements ModelDiscovery {

    private static final List<ModelDescription> KNOWN_MODELS = buildKnownModels();

    private AnthropicModelDiscovery(Builder builder) {
        // No configuration needed for static registry
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ModelDescription> discoverModels() {
        return new ArrayList<>(KNOWN_MODELS);
    }

    @Override
    public List<ModelDescription> discoverModels(ModelDiscoveryFilter filter) {
        if (filter == null || filter.matchesAll()) {
            return discoverModels();
        }

        return KNOWN_MODELS.stream()
            .filter(model -> matchesFilter(model, filter))
            .collect(Collectors.toList());
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.ANTHROPIC;
    }

    @Override
    public boolean supportsFiltering() {
        return false; // Client-side filtering only
    }

    private static List<ModelDescription> buildKnownModels() {
        List<ModelDescription> models = new ArrayList<>();

        // Claude 3.5 Sonnet (Latest)
        models.add(ModelDescription.builder()
            .id("claude-3-5-sonnet-20241022")
            .name("Claude 3.5 Sonnet")
            .description("Most intelligent model, combining high intelligence with improved speed")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .capabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
            .contextWindow(200000)
            .maxOutputTokens(8192)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .deprecated(false)
            .build());

        // Claude 3.5 Sonnet (Previous version)
        models.add(ModelDescription.builder()
            .id("claude-3-5-sonnet-20240620")
            .name("Claude 3.5 Sonnet (June 2024)")
            .description("Previous version of Claude 3.5 Sonnet")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .contextWindow(200000)
            .maxOutputTokens(8192)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .deprecated(false)
            .build());

        // Claude 3.5 Haiku
        models.add(ModelDescription.builder()
            .id("claude-3-5-haiku-20241022")
            .name("Claude 3.5 Haiku")
            .description("Fastest and most compact model for near-instant responsiveness")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .contextWindow(200000)
            .maxOutputTokens(8192)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("0.80"))
                .outputPricePerMillionTokens(new BigDecimal("4.00"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .deprecated(false)
            .build());

        // Claude 3 Opus
        models.add(ModelDescription.builder()
            .id("claude-3-opus-20240229")
            .name("Claude 3 Opus")
            .description("Top-level performance, intelligence, fluency, and understanding")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .contextWindow(200000)
            .maxOutputTokens(4096)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("15.00"))
                .outputPricePerMillionTokens(new BigDecimal("75.00"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .deprecated(false)
            .build());

        // Claude 3 Sonnet
        models.add(ModelDescription.builder()
            .id("claude-3-sonnet-20240229")
            .name("Claude 3 Sonnet")
            .description("Balance of intelligence and speed")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .contextWindow(200000)
            .maxOutputTokens(4096)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .deprecated(false)
            .build());

        // Claude 3 Haiku
        models.add(ModelDescription.builder()
            .id("claude-3-haiku-20240307")
            .name("Claude 3 Haiku")
            .description("Fast and compact model")
            .provider(ModelProvider.ANTHROPIC)
            .type(ModelType.CHAT)
            .contextWindow(200000)
            .maxOutputTokens(4096)
            .pricing(ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("0.25"))
                .outputPricePerMillionTokens(new BigDecimal("1.25"))
                .currency("USD")
                .pricingUrl("https://www.anthropic.com/pricing")
                .build())
            .owner("Anthropic")
            .deprecated(false)
            .build());

        return models;
    }

    private boolean matchesFilter(ModelDescription model, ModelDiscoveryFilter filter) {
        // Filter by type
        if (filter.getTypes() != null && !filter.getTypes().isEmpty()) {
            if (model.getType() == null || !filter.getTypes().contains(model.getType())) {
                return false;
            }
        }

        // Filter by required capabilities
        if (filter.getRequiredCapabilities() != null && !filter.getRequiredCapabilities().isEmpty()) {
            if (model.getCapabilities() == null ||
                !model.getCapabilities().containsAll(filter.getRequiredCapabilities())) {
                return false;
            }
        }

        // Filter by minimum context window
        if (filter.getMinContextWindow() != null) {
            if (model.getContextWindow() == null ||
                model.getContextWindow() < filter.getMinContextWindow()) {
                return false;
            }
        }

        // Filter by maximum context window
        if (filter.getMaxContextWindow() != null) {
            if (model.getContextWindow() == null ||
                model.getContextWindow() > filter.getMaxContextWindow()) {
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

        public Builder() {
            // No configuration needed for static registry
        }

        public AnthropicModelDiscovery build() {
            return new AnthropicModelDiscovery(this);
        }
    }
}
