package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.CacheTTL;

class BedrockChatRequestParametersTest {

    @Test
    void should_enable_prompt_caching_with_placement() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .build();

        // Then
        assertThat(params.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_SYSTEM);
        assertThat(params.additionalModelRequestFields()).isNullOrEmpty();
    }

    @Test
    void should_build_without_prompt_caching() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .temperature(0.7)
                .maxOutputTokens(100)
                .build();

        // Then
        assertThat(params.additionalModelRequestFields()).isNullOrEmpty();
        assertThat(params.temperature()).isEqualTo(0.7);
        assertThat(params.maxOutputTokens()).isEqualTo(100);
    }

    @Test
    void should_not_enable_caching_with_null_placement() {
        // Given & When
        BedrockChatRequestParameters params =
                BedrockChatRequestParameters.builder().promptCaching(null).build();

        // Then
        assertThat(params.additionalModelRequestFields()).isNullOrEmpty();
    }

    @Test
    void should_enable_caching_with_after_user_message_placement() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .build();

        // Then
        assertThat(params.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_USER_MESSAGE);
        assertThat(params.additionalModelRequestFields()).isNullOrEmpty();
    }

    @Test
    void should_enable_caching_with_after_last_user_message_placement() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_LAST_USER_MESSAGE)
                .build();

        // Then
        assertThat(params.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_LAST_USER_MESSAGE);
        assertThat(params.additionalModelRequestFields()).isNullOrEmpty();
    }

    @Test
    void should_set_different_cache_point_placements() {
        // Test AFTER_TOOLS
        BedrockChatRequestParameters paramsAfterTools = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_TOOLS)
                .build();

        assertThat(paramsAfterTools.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_TOOLS);
        assertThat(paramsAfterTools.additionalModelRequestFields()).isNullOrEmpty();

        // Test AFTER_USER_MESSAGE
        BedrockChatRequestParameters paramsAfterUser = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .build();

        assertThat(paramsAfterUser.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_USER_MESSAGE);
        assertThat(paramsAfterUser.additionalModelRequestFields()).isNullOrEmpty();

        // Test AFTER_LAST_USER_MESSAGE
        BedrockChatRequestParameters paramsAfterLastUser = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_LAST_USER_MESSAGE)
                .build();

        assertThat(paramsAfterLastUser.cachePointPlacement())
                .isEqualTo(BedrockCachePointPlacement.AFTER_LAST_USER_MESSAGE);
        assertThat(paramsAfterLastUser.additionalModelRequestFields()).isNullOrEmpty();
    }

    @Test
    void should_combine_with_other_additional_fields() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .additionalModelRequestField("customField", "customValue")
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .build();

        // Then
        assertThat(params.additionalModelRequestFields())
                .isNotNull()
                .containsEntry("customField", "customValue")
                .doesNotContainKey("promptCaching"); // promptCaching should NOT be in additionalFields
        assertThat(params.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_SYSTEM);
    }

    @Test
    void should_combine_with_reasoning() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .enableReasoning(1000)
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .build();

        // Then
        assertThat(params.additionalModelRequestFields())
                .isNotNull()
                .containsKey("reasoning_config")
                .doesNotContainKey("promptCaching"); // promptCaching should NOT be in additionalFields
        assertThat(params.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_SYSTEM);
    }

    @Test
    void should_override_with_bedrock_parameters() {
        // Given
        BedrockChatRequestParameters original = BedrockChatRequestParameters.builder()
                .temperature(0.5)
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .build();

        BedrockChatRequestParameters override = BedrockChatRequestParameters.builder()
                .temperature(0.8)
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .build();

        // When
        BedrockChatRequestParameters merged = original.overrideWith(override);

        // Then
        assertThat(merged.temperature()).isEqualTo(0.8);
        assertThat(merged.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_SYSTEM);
        assertThat(merged.additionalModelRequestFields()).isNullOrEmpty();
    }

    @Test
    void should_override_after_user_message_with_after_last_user_message() {
        // Given
        BedrockChatRequestParameters original = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .build();

        BedrockChatRequestParameters override = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_LAST_USER_MESSAGE)
                .build();

        // When
        BedrockChatRequestParameters merged = original.overrideWith(override);

        // Then
        assertThat(merged.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_LAST_USER_MESSAGE);
    }

    @Test
    void should_override_with_guardrail_parameters() {
        // Given
        BedrockChatRequestParameters original = BedrockChatRequestParameters.builder()
                .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                        .guardrailIdentifier("12345")
                        .guardrailVersion("DRAFT")
                        .build())
                .build();

        BedrockChatRequestParameters override = BedrockChatRequestParameters.builder()
                .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                        .guardrailIdentifier("67890")
                        .guardrailVersion("LIVE")
                        .build())
                .build();

        // When
        BedrockChatRequestParameters merged = original.overrideWith(override);

        // Then
        assertThat(merged.bedrockGuardrailConfiguration().guardrailIdentifier()).isEqualTo("67890");
        assertThat(merged.bedrockGuardrailConfiguration().guardrailVersion()).isEqualTo("LIVE");
    }

    @Test
    void should_build_with_service_tier() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .serviceTier(BedrockServiceTier.PRIORITY)
                .build();

        // Then
        assertThat(params.serviceTier()).isEqualTo(BedrockServiceTier.PRIORITY);
    }

    @Test
    void should_build_without_service_tier() {
        // Given & When
        BedrockChatRequestParameters params =
                BedrockChatRequestParameters.builder().temperature(0.7).build();

        // Then
        assertThat(params.serviceTier()).isNull();
    }

    @Test
    void should_set_different_service_tiers() {
        // Test PRIORITY
        BedrockChatRequestParameters paramsPriority = BedrockChatRequestParameters.builder()
                .serviceTier(BedrockServiceTier.PRIORITY)
                .build();

        assertThat(paramsPriority.serviceTier()).isEqualTo(BedrockServiceTier.PRIORITY);

        // Test DEFAULT
        BedrockChatRequestParameters paramsDefault = BedrockChatRequestParameters.builder()
                .serviceTier(BedrockServiceTier.DEFAULT)
                .build();

        assertThat(paramsDefault.serviceTier()).isEqualTo(BedrockServiceTier.DEFAULT);

        // Test FLEX
        BedrockChatRequestParameters paramsFlex = BedrockChatRequestParameters.builder()
                .serviceTier(BedrockServiceTier.FLEX)
                .build();

        assertThat(paramsFlex.serviceTier()).isEqualTo(BedrockServiceTier.FLEX);

        // Test RESERVED
        BedrockChatRequestParameters paramsReserved = BedrockChatRequestParameters.builder()
                .serviceTier(BedrockServiceTier.RESERVED)
                .build();

        assertThat(paramsReserved.serviceTier()).isEqualTo(BedrockServiceTier.RESERVED);
    }

    @Test
    void should_override_with_service_tier_parameters() {
        // Given
        BedrockChatRequestParameters original = BedrockChatRequestParameters.builder()
                .temperature(0.5)
                .serviceTier(BedrockServiceTier.DEFAULT)
                .build();

        BedrockChatRequestParameters override = BedrockChatRequestParameters.builder()
                .temperature(0.8)
                .serviceTier(BedrockServiceTier.PRIORITY)
                .build();

        // When
        BedrockChatRequestParameters merged = original.overrideWith(override);

        // Then
        assertThat(merged.temperature()).isEqualTo(0.8);
        assertThat(merged.serviceTier()).isEqualTo(BedrockServiceTier.PRIORITY);
    }

    @Test
    void should_combine_service_tier_with_other_parameters() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .serviceTier(BedrockServiceTier.FLEX)
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                        .guardrailIdentifier("12345")
                        .guardrailVersion("DRAFT")
                        .build())
                .build();

        // Then
        assertThat(params.serviceTier()).isEqualTo(BedrockServiceTier.FLEX);
        assertThat(params.cachePointPlacement()).isEqualTo(BedrockCachePointPlacement.AFTER_SYSTEM);
        assertThat(params.bedrockGuardrailConfiguration().guardrailIdentifier()).isEqualTo("12345");
    }

    @Test
    void enableAdaptiveReasoning_with_effort_should_set_reasoning_config_and_output_config() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .enableAdaptiveReasoning("low")
                .build();

        // Then
        assertThat(params.additionalModelRequestFields())
                .isNotNull()
                .containsKey("reasoning_config")
                .containsKey("output_config");

        Map<String, Object> reasoningConfig =
                (Map<String, Object>) params.additionalModelRequestFields().get("reasoning_config");
        assertThat(reasoningConfig).containsEntry("type", "adaptive").doesNotContainKey("budget_tokens");

        Map<String, Object> outputConfig =
                (Map<String, Object>) params.additionalModelRequestFields().get("output_config");
        assertThat(outputConfig).containsEntry("effort", "low");
    }

    @Test
    void enableAdaptiveReasoning_without_effort_should_only_set_reasoning_config() {
        // Given & When
        BedrockChatRequestParameters params = BedrockChatRequestParameters.builder()
                .enableAdaptiveReasoning(null)
                .build();

        // Then
        assertThat(params.additionalModelRequestFields())
                .isNotNull()
                .containsKey("reasoning_config")
                .doesNotContainKey("output_config");

        Map<String, Object> reasoningConfig =
                (Map<String, Object>) params.additionalModelRequestFields().get("reasoning_config");
        assertThat(reasoningConfig).containsEntry("type", "adaptive");
    }

    @Test
    void should_be_equal_when_all_parameters_match() {
        BedrockChatRequestParameters first = fullyPopulated().build();
        BedrockChatRequestParameters second = fullyPopulated().build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void should_not_be_equal_when_additional_model_request_fields_differ() {
        BedrockChatRequestParameters first = BedrockChatRequestParameters.builder()
                .additionalModelRequestField("a", 1)
                .build();
        BedrockChatRequestParameters second = BedrockChatRequestParameters.builder()
                .additionalModelRequestField("a", 2)
                .build();

        assertThat(first).isNotEqualTo(second);
        assertThat(first.hashCode()).isNotEqualTo(second.hashCode());
    }

    @Test
    void should_not_be_equal_when_cache_point_placement_differs() {
        BedrockChatRequestParameters first = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM)
                .build();
        BedrockChatRequestParameters second = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE)
                .build();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_not_be_equal_when_cache_ttl_differs() {
        BedrockChatRequestParameters first = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM, CacheTTL.VALUE_5_M)
                .build();
        BedrockChatRequestParameters second = BedrockChatRequestParameters.builder()
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM, CacheTTL.VALUE_1_H)
                .build();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_not_be_equal_when_guardrail_configuration_differs() {
        BedrockChatRequestParameters first = BedrockChatRequestParameters.builder()
                .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                        .guardrailIdentifier("first")
                        .guardrailVersion("1")
                        .build())
                .build();
        BedrockChatRequestParameters second = BedrockChatRequestParameters.builder()
                .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                        .guardrailIdentifier("second")
                        .guardrailVersion("1")
                        .build())
                .build();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_not_be_equal_when_service_tier_differs() {
        BedrockChatRequestParameters first = BedrockChatRequestParameters.builder()
                .serviceTier(BedrockServiceTier.DEFAULT)
                .build();
        BedrockChatRequestParameters second = BedrockChatRequestParameters.builder()
                .serviceTier(BedrockServiceTier.FLEX)
                .build();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_not_be_equal_when_an_inherited_parameter_differs() {
        BedrockChatRequestParameters first = fullyPopulated().temperature(0.1).build();
        BedrockChatRequestParameters second = fullyPopulated().temperature(0.2).build();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_include_bedrock_parameters_in_to_string() {
        BedrockChatRequestParameters parameters = fullyPopulated().build();

        assertThat(parameters.toString())
                .startsWith("BedrockChatRequestParameters{")
                .contains("additionalModelRequestFields=")
                .contains("cachePointPlacement=AFTER_SYSTEM")
                .contains("cacheTtl=")
                .contains("bedrockGuardrailConfiguration=")
                .contains("serviceTier=DEFAULT");
    }

    private static BedrockChatRequestParameters.Builder fullyPopulated() {
        return BedrockChatRequestParameters.builder()
                .modelName("model")
                .temperature(0.5)
                .additionalModelRequestField("key", "value")
                .promptCaching(BedrockCachePointPlacement.AFTER_SYSTEM, CacheTTL.VALUE_5_M)
                .guardrailConfiguration(guardrail())
                .serviceTier(BedrockServiceTier.DEFAULT);
    }

    private static BedrockGuardrailConfiguration guardrail() {
        return BedrockGuardrailConfiguration.builder()
                .guardrailIdentifier("guardrail")
                .guardrailVersion("1")
                .build();
    }
}
