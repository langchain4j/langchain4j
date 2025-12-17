package dev.langchain4j.model.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelDescriptionTest {

    @Test
    void should_build_with_required_fields_only() {
        ModelDescription description = ModelDescription.builder()
                .id("model-123")
                .name("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .build();

        assertThat(description.getId()).isEqualTo("model-123");
        assertThat(description.getName()).isEqualTo("Test Model");
        assertThat(description.getProvider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(description.getDescription()).isNull();
        assertThat(description.getType()).isNull();
        assertThat(description.getCapabilities()).isEmpty();
        assertThat(description.getPricing()).isNull();
        assertThat(description.getContextWindow()).isNull();
        assertThat(description.getMaxOutputTokens()).isNull();
        assertThat(description.getCreatedAt()).isNull();
        assertThat(description.getOwner()).isNull();
        assertThat(description.isDeprecated()).isNull();
        assertThat(description.getSupportedLanguages()).isEmpty();
        assertThat(description.getAdditionalMetadata()).isEmpty();
    }

    @Test
    void should_build_with_all_fields() {
        Instant now = Instant.now();
        ModelPricing pricing = ModelPricing.builder()
                .inputPricePerMillionTokens(new BigDecimal("3.00"))
                .outputPricePerMillionTokens(new BigDecimal("15.00"))
                .build();

        ModelDescription description = ModelDescription.builder()
                .id("model-123")
                .name("Test Model")
                .description("A test model")
                .provider(ModelProvider.OPEN_AI)
                .type(ModelType.CHAT)
                .capabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                .pricing(pricing)
                .contextWindow(128000)
                .maxOutputTokens(4096)
                .createdAt(now)
                .owner("test-org")
                .deprecated(false)
                .supportedLanguages(Set.of("en", "fr", "es"))
                .additionalMetadata(Map.of("custom", "value"))
                .build();

        assertThat(description.getId()).isEqualTo("model-123");
        assertThat(description.getName()).isEqualTo("Test Model");
        assertThat(description.getDescription()).isEqualTo("A test model");
        assertThat(description.getProvider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(description.getType()).isEqualTo(ModelType.CHAT);
        assertThat(description.getCapabilities()).containsExactly(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        assertThat(description.getPricing()).isEqualTo(pricing);
        assertThat(description.getContextWindow()).isEqualTo(128000);
        assertThat(description.getMaxOutputTokens()).isEqualTo(4096);
        assertThat(description.getCreatedAt()).isEqualTo(now);
        assertThat(description.getOwner()).isEqualTo("test-org");
        assertThat(description.isDeprecated()).isFalse();
        assertThat(description.getSupportedLanguages()).containsExactlyInAnyOrder("en", "fr", "es");
        assertThat(description.getAdditionalMetadata()).containsEntry("custom", "value");
    }

    @Test
    void should_require_id() {
        assertThatThrownBy(() -> ModelDescription.builder()
                        .name("Test Model")
                        .provider(ModelProvider.OPEN_AI)
                        .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    void should_require_name() {
        assertThatThrownBy(() -> ModelDescription.builder()
                        .id("model-123")
                        .provider(ModelProvider.OPEN_AI)
                        .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name must not be null");
    }

    @Test
    void should_require_provider() {
        assertThatThrownBy(() -> ModelDescription.builder()
                        .id("model-123")
                        .name("Test Model")
                        .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider must not be null");
    }

    @Test
    void should_implement_equals_based_on_id_and_provider() {
        ModelDescription description1 = ModelDescription.builder()
                .id("model-123")
                .name("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .build();

        ModelDescription description2 = ModelDescription.builder()
                .id("model-123")
                .name("Different Name")
                .provider(ModelProvider.OPEN_AI)
                .build();

        ModelDescription description3 = ModelDescription.builder()
                .id("model-456")
                .name("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .build();

        ModelDescription description4 = ModelDescription.builder()
                .id("model-123")
                .name("Test Model")
                .provider(ModelProvider.ANTHROPIC)
                .build();

        assertThat(description1).isEqualTo(description2);
        assertThat(description1).hasSameHashCodeAs(description2);
        assertThat(description1).isNotEqualTo(description3);
        assertThat(description1).isNotEqualTo(description4);
    }

    @Test
    void should_implement_toString() {
        ModelDescription description = ModelDescription.builder()
                .id("model-123")
                .name("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .type(ModelType.CHAT)
                .contextWindow(128000)
                .build();

        String str = description.toString();

        assertThat(str).contains("model-123");
        assertThat(str).contains("Test Model");
        assertThat(str).contains("OPEN_AI");
        assertThat(str).contains("CHAT");
        assertThat(str).contains("128000");
    }

    @Test
    void should_make_immutable_copies_of_collections() {
        Set<Capability> capabilities = Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        Set<String> languages = Set.of("en", "fr");
        Map<String, Object> metadata = Map.of("key", "value");

        ModelDescription description = ModelDescription.builder()
                .id("model-123")
                .name("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .capabilities(capabilities)
                .supportedLanguages(languages)
                .additionalMetadata(metadata)
                .build();

        assertThat(description.getCapabilities()).isNotSameAs(capabilities);
        assertThat(description.getSupportedLanguages()).isNotSameAs(languages);
        assertThat(description.getAdditionalMetadata()).isNotSameAs(metadata);
    }
}
