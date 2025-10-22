package dev.langchain4j.agent.tool;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class LazyEvaluationConfigTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should create default configuration")
        void shouldCreateDefaultConfiguration() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder().build();

            assertThat(config.mode()).isEqualTo(LazyEvaluationMode.DISABLED);
            assertThat(config.lazyTools()).isEmpty();
            assertThat(config.eagerTools()).isEmpty();
            assertThat(config.isPerformanceMonitoringEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should create configuration with all parameters")
        void shouldCreateConfigurationWithAllParameters() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .addLazyTool("tool1")
                    .addEagerTool("tool2")
                    .enablePerformanceMonitoring(true)
                    .build();

            assertThat(config.mode()).isEqualTo(LazyEvaluationMode.ENABLED);
            assertThat(config.lazyTools()).containsExactly("tool1");
            assertThat(config.eagerTools()).containsExactly("tool2");
            assertThat(config.isPerformanceMonitoringEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should support method chaining")
        void shouldSupportMethodChaining() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.AUTO)
                    .addLazyTool("lazy1")
                    .addLazyTool("lazy2")
                    .addEagerTool("eager1")
                    .enablePerformanceMonitoring(true)
                    .build();

            assertThat(config.mode()).isEqualTo(LazyEvaluationMode.AUTO);
            assertThat(config.lazyTools()).containsExactlyInAnyOrder("lazy1", "lazy2");
            assertThat(config.eagerTools()).containsExactly("eager1");
            assertThat(config.isPerformanceMonitoringEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should add multiple lazy tools at once")
        void shouldAddMultipleLazyToolsAtOnce() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .addLazyTools("tool1", "tool2", "tool3")
                    .build();

            assertThat(config.lazyTools()).containsExactlyInAnyOrder("tool1", "tool2", "tool3");
        }

        @Test
        @DisplayName("Should add multiple eager tools at once")
        void shouldAddMultipleEagerToolsAtOnce() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .addEagerTools("tool1", "tool2", "tool3")
                    .build();

            assertThat(config.eagerTools()).containsExactlyInAnyOrder("tool1", "tool2", "tool3");
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should reject null mode")
        void shouldRejectNullMode() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder().mode(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mode");
        }

        @Test
        @DisplayName("Should reject null tool name for lazy tools")
        void shouldRejectNullToolNameForLazyTools() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder().addLazyTool(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("toolName");
        }

        @Test
        @DisplayName("Should reject empty tool name for lazy tools")
        void shouldRejectEmptyToolNameForLazyTools() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder().addLazyTool(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Should reject whitespace-only tool name for lazy tools")
        void shouldRejectWhitespaceOnlyToolNameForLazyTools() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder().addLazyTool("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Should reject null tool name for eager tools")
        void shouldRejectNullToolNameForEagerTools() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder().addEagerTool(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("toolName");
        }

        @Test
        @DisplayName("Should reject empty tool name for eager tools")
        void shouldRejectEmptyToolNameForEagerTools() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder().addEagerTool(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Should reject tools in both lazy and eager sets")
        void shouldRejectToolsInBothLazyAndEagerSets() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder()
                            .addLazyTool("conflictTool")
                            .addEagerTool("conflictTool")
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("both lazy and eager")
                    .hasMessageContaining("conflictTool");
        }

        @Test
        @DisplayName("Should reject lazy tools in IMMEDIATE_ONLY mode")
        void shouldRejectLazyToolsInImmediateOnlyMode() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder()
                            .mode(LazyEvaluationMode.IMMEDIATE_ONLY)
                            .addLazyTool("someTool")
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IMMEDIATE_ONLY mode cannot have explicit lazy tools");
        }

        @Test
        @DisplayName("Should reject null array for multiple lazy tools")
        void shouldRejectNullArrayForMultipleLazyTools() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder().addLazyTools((String[]) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("toolNames");
        }

        @Test
        @DisplayName("Should reject null array for multiple eager tools")
        void shouldRejectNullArrayForMultipleEagerTools() {
            assertThatThrownBy(() -> LazyEvaluationConfig.builder().addEagerTools((String[]) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("toolNames");
        }
    }

    @Nested
    @DisplayName("shouldUseLazyEvaluation Tests")
    class ShouldUseLazyEvaluationTests {

        @Test
        @DisplayName("Should reject null tool name")
        void shouldRejectNullToolName() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder().build();

            assertThatThrownBy(() -> config.shouldUseLazyEvaluation(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("toolName");
        }

        @Test
        @DisplayName("Should prioritize eager tools over mode")
        void shouldPrioritizeEagerToolsOverMode() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .addEagerTool("eagerTool")
                    .build();

            assertThat(config.shouldUseLazyEvaluation("eagerTool")).isFalse();
        }

        @Test
        @DisplayName("Should prioritize lazy tools over mode")
        void shouldPrioritizeLazyToolsOverMode() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.DISABLED)
                    .addLazyTool("lazyTool")
                    .build();

            assertThat(config.shouldUseLazyEvaluation("lazyTool")).isTrue();
        }

        @Test
        @DisplayName("Should prioritize eager tools over lazy tools")
        void shouldPrioritizeEagerToolsOverLazyTools() {
            // This test verifies that validation prevents conflicting tool assignments
            // The validation should occur during build(), not after
            assertThatThrownBy(() -> LazyEvaluationConfig.builder()
                            .mode(LazyEvaluationMode.ENABLED)
                            .addLazyTool("conflictTool")
                            .addEagerTool("conflictTool")
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("both lazy and eager")
                    .hasMessageContaining("conflictTool");
        }

        @ParameterizedTest
        @EnumSource(LazyEvaluationMode.class)
        @DisplayName("Should handle all modes for unknown tools")
        void shouldHandleAllModesForUnknownTools(LazyEvaluationMode mode) {
            LazyEvaluationConfig config =
                    LazyEvaluationConfig.builder().mode(mode).build();

            boolean result = config.shouldUseLazyEvaluation("unknownTool");

            switch (mode) {
                case DISABLED, IMMEDIATE_ONLY -> assertThat(result).isFalse();
                case ENABLED -> assertThat(result).isTrue();
                case AUTO -> {
                    // AUTO mode uses heuristics, so we test specific patterns
                    assertThat(config.shouldUseLazyEvaluation("expensiveTool")).isTrue();
                    assertThat(config.shouldUseLazyEvaluation("simpleTool")).isFalse();
                }
            }
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "expensiveCalculation",
                    "slowProcess",
                    "heavyComputation",
                    "complexAnalysis",
                    "dataAnalysis",
                    "performCalculation"
                })
        @DisplayName("Should use lazy evaluation for expensive-sounding tools in AUTO mode")
        void shouldUseLazyEvaluationForExpensiveToolsInAutoMode(String toolName) {
            LazyEvaluationConfig config =
                    LazyEvaluationConfig.builder().mode(LazyEvaluationMode.AUTO).build();

            assertThat(config.shouldUseLazyEvaluation(toolName)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"simpleTool", "quickCheck", "basicValidation", "fastLookup"})
        @DisplayName("Should not use lazy evaluation for simple tools in AUTO mode")
        void shouldNotUseLazyEvaluationForSimpleToolsInAutoMode(String toolName) {
            LazyEvaluationConfig config =
                    LazyEvaluationConfig.builder().mode(LazyEvaluationMode.AUTO).build();

            assertThat(config.shouldUseLazyEvaluation(toolName)).isFalse();
        }
    }

    @Nested
    @DisplayName("Mode-Specific Tests")
    class ModeSpecificTests {

        @Test
        @DisplayName("DISABLED mode should never use lazy evaluation")
        void disabledModeShouldNeverUseLazyEvaluation() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.DISABLED)
                    .build();

            assertThat(config.shouldUseLazyEvaluation("anyTool")).isFalse();
            assertThat(config.shouldUseLazyEvaluation("expensiveTool")).isFalse();
        }

        @Test
        @DisplayName("IMMEDIATE_ONLY mode should never use lazy evaluation")
        void immediateOnlyModeShouldNeverUseLazyEvaluation() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.IMMEDIATE_ONLY)
                    .build();

            assertThat(config.shouldUseLazyEvaluation("anyTool")).isFalse();
            assertThat(config.shouldUseLazyEvaluation("expensiveTool")).isFalse();
        }

        @Test
        @DisplayName("ENABLED mode should always use lazy evaluation")
        void enabledModeShouldAlwaysUseLazyEvaluation() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .build();

            assertThat(config.shouldUseLazyEvaluation("anyTool")).isTrue();
            assertThat(config.shouldUseLazyEvaluation("simpleTool")).isTrue();
        }

        @Test
        @DisplayName("AUTO mode should use heuristics")
        void autoModeShouldUseHeuristics() {
            LazyEvaluationConfig config =
                    LazyEvaluationConfig.builder().mode(LazyEvaluationMode.AUTO).build();

            // Test heuristic patterns
            assertThat(config.shouldUseLazyEvaluation("expensiveOperation")).isTrue();
            assertThat(config.shouldUseLazyEvaluation("slowProcess")).isTrue();
            assertThat(config.shouldUseLazyEvaluation("heavyTask")).isTrue();
            assertThat(config.shouldUseLazyEvaluation("complexCalculation")).isTrue();
            assertThat(config.shouldUseLazyEvaluation("dataAnalysis")).isTrue();

            assertThat(config.shouldUseLazyEvaluation("quickCheck")).isFalse();
            assertThat(config.shouldUseLazyEvaluation("simpleTool")).isFalse();
        }
    }

    @Nested
    @DisplayName("Static Factory Methods Tests")
    class StaticFactoryMethodsTests {

        @Test
        @DisplayName("defaultConfig should return DISABLED mode configuration")
        void defaultConfigShouldReturnDisabledModeConfiguration() {
            LazyEvaluationConfig config = LazyEvaluationConfig.defaultConfig();

            assertThat(config.mode()).isEqualTo(LazyEvaluationMode.DISABLED);
            assertThat(config.lazyTools()).isEmpty();
            assertThat(config.eagerTools()).isEmpty();
            assertThat(config.isPerformanceMonitoringEnabled()).isFalse();
        }

        @Test
        @DisplayName("builder should return new builder instance")
        void builderShouldReturnNewBuilderInstance() {
            LazyEvaluationConfig.Builder builder1 = LazyEvaluationConfig.builder();
            LazyEvaluationConfig.Builder builder2 = LazyEvaluationConfig.builder();

            assertThat(builder1).isNotSameAs(builder2);
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("lazyTools should return unmodifiable set")
        void lazyToolsShouldReturnUnmodifiableSet() {
            LazyEvaluationConfig config =
                    LazyEvaluationConfig.builder().addLazyTool("tool1").build();

            assertThatThrownBy(() -> config.lazyTools().add("tool2")).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("eagerTools should return unmodifiable set")
        void eagerToolsShouldReturnUnmodifiableSet() {
            LazyEvaluationConfig config =
                    LazyEvaluationConfig.builder().addEagerTool("tool1").build();

            assertThatThrownBy(() -> config.eagerTools().add("tool2"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Should be equal when all properties are the same")
        void shouldBeEqualWhenAllPropertiesAreTheSame() {
            LazyEvaluationConfig config1 = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .addLazyTool("tool1")
                    .addEagerTool("tool2")
                    .enablePerformanceMonitoring(true)
                    .build();

            LazyEvaluationConfig config2 = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .addLazyTool("tool1")
                    .addEagerTool("tool2")
                    .enablePerformanceMonitoring(true)
                    .build();

            assertThat(config1).isEqualTo(config2);
            assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when modes differ")
        void shouldNotBeEqualWhenModesDiffer() {
            LazyEvaluationConfig config1 = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .build();

            LazyEvaluationConfig config2 = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.DISABLED)
                    .build();

            assertThat(config1).isNotEqualTo(config2);
        }

        @Test
        @DisplayName("Should not be equal when lazy tools differ")
        void shouldNotBeEqualWhenLazyToolsDiffer() {
            LazyEvaluationConfig config1 =
                    LazyEvaluationConfig.builder().addLazyTool("tool1").build();

            LazyEvaluationConfig config2 =
                    LazyEvaluationConfig.builder().addLazyTool("tool2").build();

            assertThat(config1).isNotEqualTo(config2);
        }

        @Test
        @DisplayName("Should not be equal when performance monitoring differs")
        void shouldNotBeEqualWhenPerformanceMonitoringDiffers() {
            LazyEvaluationConfig config1 = LazyEvaluationConfig.builder()
                    .enablePerformanceMonitoring(true)
                    .build();

            LazyEvaluationConfig config2 = LazyEvaluationConfig.builder()
                    .enablePerformanceMonitoring(false)
                    .build();

            assertThat(config1).isNotEqualTo(config2);
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString should contain all configuration details")
        void toStringShouldContainAllConfigurationDetails() {
            LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                    .mode(LazyEvaluationMode.ENABLED)
                    .addLazyTool("lazyTool")
                    .addEagerTool("eagerTool")
                    .enablePerformanceMonitoring(true)
                    .build();

            String toString = config.toString();

            assertThat(toString)
                    .contains("LazyEvaluationConfig")
                    .contains("mode=ENABLED")
                    .contains("lazyTool")
                    .contains("eagerTool")
                    .contains("performanceMonitoringEnabled=true");
        }
    }
}
