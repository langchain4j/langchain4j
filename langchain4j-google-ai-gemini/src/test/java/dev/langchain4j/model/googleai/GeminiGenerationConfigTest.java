package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThatCharSequence;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeminiGenerationConfigTest {

    @Nested
    class Builder {

        @Test
        void settingSeedProducesConfigWithSeed() {
            GeminiGenerationConfig result =
                    GeminiGenerationConfig.builder().seed(42).build();

            assertThatCharSequence(Json.toJson(result)).contains("\"seed\" : 42");
        }

        @Test
        void defaultValues() {
            GeminiGenerationConfig result = GeminiGenerationConfig.builder().build();

            assertThatCharSequence(Json.toJson(result)).doesNotContain("\"seed\"");
        }

        @Test
        void settingMediaResolutionProducesConfigWithMediaResolution() {
            GeminiGenerationConfig result = GeminiGenerationConfig.builder()
                    .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_HIGH)
                    .build();

            assertThatCharSequence(Json.toJson(result)).contains("\"mediaResolution\" : \"MEDIA_RESOLUTION_HIGH\"");
        }

        @Test
        void settingMediaResolutionLowProducesCorrectJson() {
            GeminiGenerationConfig result = GeminiGenerationConfig.builder()
                    .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_LOW)
                    .build();

            assertThatCharSequence(Json.toJson(result)).contains("\"mediaResolution\" : \"MEDIA_RESOLUTION_LOW\"");
        }

        @Test
        void settingMediaResolutionMediumProducesCorrectJson() {
            GeminiGenerationConfig result = GeminiGenerationConfig.builder()
                    .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_MEDIUM)
                    .build();

            assertThatCharSequence(Json.toJson(result)).contains("\"mediaResolution\" : \"MEDIA_RESOLUTION_MEDIUM\"");
        }

        @Test
        void settingMediaResolutionUltraHighProducesCorrectJson() {
            GeminiGenerationConfig result = GeminiGenerationConfig.builder()
                    .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_ULTRA_HIGH)
                    .build();

            assertThatCharSequence(Json.toJson(result))
                    .contains("\"mediaResolution\" : \"MEDIA_RESOLUTION_ULTRA_HIGH\"");
        }

        @Test
        void settingMediaResolutionUnspecifiedProducesCorrectJson() {
            GeminiGenerationConfig result = GeminiGenerationConfig.builder()
                    .mediaResolution(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_UNSPECIFIED)
                    .build();

            assertThatCharSequence(Json.toJson(result))
                    .contains("\"mediaResolution\" : \"MEDIA_RESOLUTION_UNSPECIFIED\"");
        }
    }
}
