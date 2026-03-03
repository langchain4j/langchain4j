package dev.langchain4j.store.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.spi.services.prompt.PromptResourceLoader;
import org.junit.jupiter.api.Test;

class PromptResourceLoaderRegistryTest {

    @Test
    void should_create_registry_with_additional_loaders() {
        // Given
        PromptResourceLoader customLoader = createLoader("test", "custom content", 100);

        // When
        PromptResourceLoaderRegistry registry = PromptResourceLoaderRegistry.with(customLoader);

        // Then
        String result = registry.loadResource("test:my-resource", PromptResourceLoaderRegistryTest.class);
        assertThat(result).isEqualTo("custom content");
    }

    @Test
    void should_create_registry_with_only_provided_loaders() {
        // Given
        PromptResourceLoader loader1 = createLoader("custom1", "content from loader1", 50);
        PromptResourceLoader loader2 = createLoader("custom2", "content from loader2", 100);

        // When
        PromptResourceLoaderRegistry registry = PromptResourceLoaderRegistry.of(loader1, loader2);

        // Then
        String result1 = registry.loadResource("custom1:my-resource", PromptResourceLoaderRegistryTest.class);
        assertThat(result1).isEqualTo("content from loader1");

        String result2 = registry.loadResource("custom2:my-resource", PromptResourceLoaderRegistryTest.class);
        assertThat(result2).isEqualTo("content from loader2");
    }

    @Test
    void should_use_higher_priority_loader_when_multiple_loaders_support_same_protocol() {
        // Given
        PromptResourceLoader lowerPriorityLoader = createLoader("test", "low priority content", 10);
        PromptResourceLoader higherPriorityLoader = createLoader("test", "high priority content", 100);

        // When
        PromptResourceLoaderRegistry registry =
                PromptResourceLoaderRegistry.of(lowerPriorityLoader, higherPriorityLoader);

        // Then
        String result = registry.loadResource("test:my-resource", PromptResourceLoaderRegistryTest.class);
        assertThat(result).isEqualTo("high priority content");
    }

    @Test
    void should_fail_when_no_loader_supports_protocol() {
        // Given
        PromptResourceLoader loader = createLoader("test", "test content");

        PromptResourceLoaderRegistry registry = PromptResourceLoaderRegistry.of(loader);

        // When/Then
        assertThatThrownBy(() -> registry.loadResource("unknown:my-resource", PromptResourceLoaderRegistryTest.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("No loader found for protocol 'unknown'");
    }

    @Test
    void should_include_spi_loaders_when_using_with() {
        // Given: MockPromptResourceLoader is registered via SPI
        PromptResourceLoader customLoader = createLoader("custom", "custom content");

        // When
        PromptResourceLoaderRegistry registry = PromptResourceLoaderRegistry.with(customLoader);

        // Then: Should support both SPI-discovered and custom loaders
        String customResult = registry.loadResource("custom:my-resource", PromptResourceLoaderRegistryTest.class);
        assertThat(customResult).isEqualTo("custom content");

        // MockPromptResourceLoader is discovered via SPI
        String mockResult = registry.loadResource("mock:simple-recipe", PromptResourceLoaderRegistryTest.class);
        assertThat(mockResult).isEqualTo("Create a simple recipe using {{it}}");
    }

    @Test
    void should_not_include_spi_loaders_when_using_of() {
        // Given
        PromptResourceLoader customLoader = createLoader("custom", "custom content");

        // When
        PromptResourceLoaderRegistry registry = PromptResourceLoaderRegistry.of(customLoader);

        // Then: Should only support custom loader, not SPI-discovered ones
        String customResult = registry.loadResource("custom:my-resource", PromptResourceLoaderRegistryTest.class);
        assertThat(customResult).isEqualTo("custom content");

        // MockPromptResourceLoader should NOT be available (not discovered via SPI)
        assertThatThrownBy(() -> registry.loadResource("mock:simple-recipe", PromptResourceLoaderRegistryTest.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("No loader found for protocol 'mock'");
    }

    private static PromptResourceLoader createLoader(String protocol, String content) {
        return createLoader(protocol, content, 0);
    }

    private static PromptResourceLoader createLoader(String protocol, String content, int priority) {
        return new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return protocol;
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return content;
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }
}
