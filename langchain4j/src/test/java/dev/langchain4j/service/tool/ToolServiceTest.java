package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.LazyEvaluationConfig;
import dev.langchain4j.agent.tool.LazyEvaluationMode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ToolServiceTest {

    @Nested
    @DisplayName("Configuration Propagation Tests")
    class ConfigurationPropagationTests {

        private static class TestTool {
            @Tool("Test tool for configuration propagation")
            public String testMethod() {
                return "test result";
            }
        }

        @Test
        @DisplayName("Should propagate LazyEvaluationConfig to DefaultToolExecutor")
        void shouldPropagateLazyEvaluationConfigToDefaultToolExecutor() throws NoSuchMethodException {
            // Given
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .build();
            
            ToolService toolService = new ToolService();
            toolService.lazyEvaluationConfig(config);
            
            TestTool testTool = new TestTool();
            Method testMethod = TestTool.class.getDeclaredMethod("testMethod");

            // When
            ToolExecutor executor = toolService.createToolExecutor(testTool, testMethod, config);

            // Then
            assertThat(executor).isInstanceOf(DefaultToolExecutor.class);
            DefaultToolExecutor defaultExecutor = (DefaultToolExecutor) executor;
            assertThat(defaultExecutor.lazyEvaluationConfig()).isEqualTo(config);
        }

        @Test
        @DisplayName("Should use default config when none provided to createToolExecutor")
        void shouldUseDefaultConfigWhenNoneProvidedToCreateToolExecutor() throws NoSuchMethodException {
            // Given
            ToolService toolService = new ToolService();
            TestTool testTool = new TestTool();
            Method testMethod = TestTool.class.getDeclaredMethod("testMethod");

            // When
            ToolExecutor executor = toolService.createToolExecutor(testTool, testMethod);

            // Then
            assertThat(executor).isInstanceOf(DefaultToolExecutor.class);
            DefaultToolExecutor defaultExecutor = (DefaultToolExecutor) executor;
            assertThat(defaultExecutor.lazyEvaluationConfig()).isEqualTo(LazyEvaluationConfig.defaultConfig());
        }

        @Test
        @DisplayName("Should use service-level config when none provided to createToolExecutor")
        void shouldUseServiceLevelConfigWhenNoneProvidedToCreateToolExecutor() throws NoSuchMethodException {
            // Given
            LazyEvaluationConfig serviceConfig = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.DISABLED)
                    .build();
            
            ToolService toolService = new ToolService();
            toolService.lazyEvaluationConfig(serviceConfig);
            
            TestTool testTool = new TestTool();
            Method testMethod = TestTool.class.getDeclaredMethod("testMethod");

            // When
            ToolExecutor executor = toolService.createToolExecutor(testTool, testMethod);

            // Then
            assertThat(executor).isInstanceOf(DefaultToolExecutor.class);
            DefaultToolExecutor defaultExecutor = (DefaultToolExecutor) executor;
            assertThat(defaultExecutor.lazyEvaluationConfig()).isEqualTo(serviceConfig);
        }

        @Test
        @DisplayName("Should override service-level config with method parameter")
        void shouldOverrideServiceLevelConfigWithMethodParameter() throws NoSuchMethodException {
            // Given
            LazyEvaluationConfig serviceConfig = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.DISABLED)
                    .build();
            
            LazyEvaluationConfig methodConfig = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .build();
            
            ToolService toolService = new ToolService();
            toolService.lazyEvaluationConfig(serviceConfig);
            
            TestTool testTool = new TestTool();
            Method testMethod = TestTool.class.getDeclaredMethod("testMethod");

            // When
            ToolExecutor executor = toolService.createToolExecutor(testTool, testMethod, methodConfig);

            // Then
            assertThat(executor).isInstanceOf(DefaultToolExecutor.class);
            DefaultToolExecutor defaultExecutor = (DefaultToolExecutor) executor;
            assertThat(defaultExecutor.lazyEvaluationConfig()).isEqualTo(methodConfig);
        }

        @Test
        @DisplayName("Should return default config when no config is set")
        void shouldReturnDefaultConfigWhenNoConfigIsSet() {
            // Given
            ToolService toolService = new ToolService();

            // When
            LazyEvaluationConfig config = toolService.lazyEvaluationConfig();

            // Then
            assertThat(config).isEqualTo(LazyEvaluationConfig.defaultConfig());
        }

        @Test
        @DisplayName("Should return set config when config is explicitly set")
        void shouldReturnSetConfigWhenConfigIsExplicitlySet() {
            // Given
            LazyEvaluationConfig expectedConfig = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.AUTO)
                    .build();
            
            ToolService toolService = new ToolService();
            toolService.lazyEvaluationConfig(expectedConfig);

            // When
            LazyEvaluationConfig actualConfig = toolService.lazyEvaluationConfig();

            // Then
            assertThat(actualConfig).isEqualTo(expectedConfig);
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        private static class TestTool {
            @Tool("Test tool for backward compatibility")
            public String testMethod() {
                return "test result";
            }
        }

        @Test
        @DisplayName("Should maintain backward compatibility with existing createToolExecutor usage")
        void shouldMaintainBackwardCompatibilityWithExistingCreateToolExecutorUsage() throws NoSuchMethodException {
            // Given
            ToolService toolService = new ToolService();
            TestTool testTool = new TestTool();
            Method testMethod = TestTool.class.getDeclaredMethod("testMethod");

            // When - using the original method signature
            ToolExecutor executor = toolService.createToolExecutor(testTool, testMethod);

            // Then
            assertThat(executor).isInstanceOf(DefaultToolExecutor.class);
            assertThat(executor).isNotNull();
            
            // Verify it can execute tools
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("testMethod")
                    .arguments("{}")
                    .build();
            
            String result = executor.execute(request, "DEFAULT");
            assertThat(result).isEqualTo("test result");
        }

        @Test
        @DisplayName("Should work with processToolMethod integration")
        void shouldWorkWithProcessToolMethodIntegration() throws NoSuchMethodException {
            // Given
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .build();
            
            ToolService toolService = new ToolService();
            toolService.lazyEvaluationConfig(config);
            
            TestTool testTool = new TestTool();
            Method testMethod = TestTool.class.getDeclaredMethod("testMethod");

            // When - this simulates what processToolMethod does internally
            ToolExecutor executor = toolService.createToolExecutor(testTool, testMethod, toolService.lazyEvaluationConfig());

            // Then
            assertThat(executor).isInstanceOf(DefaultToolExecutor.class);
            DefaultToolExecutor defaultExecutor = (DefaultToolExecutor) executor;
            assertThat(defaultExecutor.lazyEvaluationConfig()).isEqualTo(config);
        }
    }
}