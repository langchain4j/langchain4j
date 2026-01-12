package dev.langchain4j.model.catalog;

import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.model.ModelProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelDescriptionTest {

    @Test
    void should_build_with_required_fields_only() {
        ModelDescription description = ModelDescription.builder()
                .name("model-123")
                .displayName("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .build();

        assertThat(description.name()).isEqualTo("model-123");
        assertThat(description.displayName()).isEqualTo("Test Model");
        assertThat(description.provider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(description.description()).isNull();
        assertThat(description.type()).isNull();
        assertThat(description.maxInputTokens()).isNull();
        assertThat(description.maxOutputTokens()).isNull();
        assertThat(description.createdAt()).isNull();
        assertThat(description.owner()).isNull();
    }

    @Test
    void should_build_with_all_fields() {
        Instant now = Instant.now();

        ModelDescription description = ModelDescription.builder()
                .name("model-123")
                .displayName("Test Model")
                .description("A test model")
                .provider(ModelProvider.OPEN_AI)
                .type(ModelType.CHAT)
                .maxInputTokens(120000)
                .maxOutputTokens(4096)
                .createdAt(now)
                .owner("test-org")
                .build();

        assertThat(description.name()).isEqualTo("model-123");
        assertThat(description.displayName()).isEqualTo("Test Model");
        assertThat(description.description()).isEqualTo("A test model");
        assertThat(description.provider()).isEqualTo(ModelProvider.OPEN_AI);
        assertThat(description.type()).isEqualTo(ModelType.CHAT);
        assertThat(description.maxInputTokens()).isEqualTo(120000);
        assertThat(description.maxOutputTokens()).isEqualTo(4096);
        assertThat(description.createdAt()).isEqualTo(now);
        assertThat(description.owner()).isEqualTo("test-org");
    }

    @Test
    void should_require_name() {
        assertThatThrownBy(() -> ModelDescription.builder()
                        .provider(ModelProvider.OPEN_AI)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be null");
    }

    @Test
    void should_require_provider() {
        assertThatThrownBy(() -> ModelDescription.builder()
                        .name("model-123")
                        .displayName("Test Model")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider cannot be null");
    }

    @Test
    void should_implement_equals_based_on_id_and_provider() {
        ModelDescription description1 = ModelDescription.builder()
                .name("model-123")
                .displayName("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .build();

        ModelDescription description2 = ModelDescription.builder()
                .name("model-123")
                .displayName("Different Name")
                .provider(ModelProvider.OPEN_AI)
                .build();

        ModelDescription description3 = ModelDescription.builder()
                .name("model-456")
                .displayName("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .build();

        ModelDescription description4 = ModelDescription.builder()
                .name("model-123")
                .displayName("Test Model")
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
                .name("model-123")
                .displayName("Test Model")
                .provider(ModelProvider.OPEN_AI)
                .type(ModelType.CHAT)
                .maxInputTokens(128000)
                .build();

        String str = description.toString();

        assertThat(str).contains("model-123");
        assertThat(str).contains("Test Model");
        assertThat(str).contains("OPEN_AI");
        assertThat(str).contains("CHAT");
        assertThat(str).contains("128000");
    }
}
