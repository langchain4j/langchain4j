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
        PromptResourceLoader customLoader = new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return "test";
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return "custom content";
            }

            @Override
            public int getPriority() {
                return 100;
            }
        };

        // When
        PromptResourceLoaderRegistry registry = PromptResourceLoaderRegistry.with(customLoader);

        // Then
        String result = registry.loadResource("test:my-resource", PromptResourceLoaderRegistryTest.class);
        assertThat(result).isEqualTo("custom content");
    }

    @Test
    void should_create_registry_with_only_provided_loaders() {
        // Given
        PromptResourceLoader loader1 = new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return "custom1";
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return "content from loader1";
            }

            @Override
            public int getPriority() {
                return 50;
            }
        };

        PromptResourceLoader loader2 = new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return "custom2";
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return "content from loader2";
            }

            @Override
            public int getPriority() {
                return 100;
            }
        };

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
        PromptResourceLoader lowerPriorityLoader = new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return "test";
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return "low priority content";
            }

            @Override
            public int getPriority() {
                return 10;
            }
        };

        PromptResourceLoader higherPriorityLoader = new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return "test";
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return "high priority content";
            }

            @Override
            public int getPriority() {
                return 100;
            }
        };

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
        PromptResourceLoader loader = new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return "test";
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return "test content";
            }
        };

        PromptResourceLoaderRegistry registry = PromptResourceLoaderRegistry.of(loader);

        // When/Then
        assertThatThrownBy(() -> registry.loadResource("unknown:my-resource", PromptResourceLoaderRegistryTest.class))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("No loader found for protocol 'unknown'");
    }

    @Test
    void should_include_spi_loaders_when_using_with() {
        // Given: MockPromptResourceLoader is registered via SPI
        PromptResourceLoader customLoader = new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return "custom";
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return "custom content";
            }
        };

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
        PromptResourceLoader customLoader = new PromptResourceLoader() {
            @Override
            public String getProtocol() {
                return "custom";
            }

            @Override
            public String loadResource(String resource, Class<?> contextClass) {
                return "custom content";
            }
        };

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
}
