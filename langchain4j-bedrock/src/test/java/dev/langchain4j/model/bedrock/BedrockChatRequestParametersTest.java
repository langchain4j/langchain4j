package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
