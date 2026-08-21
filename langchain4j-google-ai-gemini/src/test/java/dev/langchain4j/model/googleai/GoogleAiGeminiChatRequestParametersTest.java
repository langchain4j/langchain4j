package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiChatRequestParametersTest {

    @Test
    void should_build_with_gemini_specific_parameters() {
        GoogleAiGeminiChatRequestParameters parameters = GoogleAiGeminiChatRequestParameters.builder()
                .temperature(0.7)
                .aspectRatio("16:9")
                .imageSize("2K")
                .cachedContentName("cachedContents/abc123")
                .build();

        assertThat(parameters.temperature()).isEqualTo(0.7);
        assertThat(parameters.aspectRatio()).isEqualTo("16:9");
        assertThat(parameters.imageSize()).isEqualTo("2K");
        assertThat(parameters.cachedContentName()).isEqualTo("cachedContents/abc123");
    }

    @Test
    void should_default_gemini_specific_parameters_to_null() {
        assertThat(GoogleAiGeminiChatRequestParameters.EMPTY.aspectRatio()).isNull();
        assertThat(GoogleAiGeminiChatRequestParameters.EMPTY.imageSize()).isNull();
        assertThat(GoogleAiGeminiChatRequestParameters.EMPTY.cachedContentName())
                .isNull();
    }

    @Test
    void image_aspect_ratio_should_be_an_alias_for_aspect_ratio() {
        GoogleAiGeminiChatRequestParameters parameters = GoogleAiGeminiChatRequestParameters.builder()
                .imageAspectRatio("4:3")
                .build();

        assertThat(parameters.aspectRatio()).isEqualTo("4:3");
    }

    @Test
    void overrideWith_should_override_gemini_specific_parameters() {
        GoogleAiGeminiChatRequestParameters original = GoogleAiGeminiChatRequestParameters.builder()
                .aspectRatio("1:1")
                .imageSize("1K")
                .cachedContentName("cachedContents/original")
                .build();

        GoogleAiGeminiChatRequestParameters override = GoogleAiGeminiChatRequestParameters.builder()
                .aspectRatio("16:9")
                .cachedContentName("cachedContents/override")
                .build();

        GoogleAiGeminiChatRequestParameters result = original.overrideWith(override);

        assertThat(result.aspectRatio()).isEqualTo("16:9");
        assertThat(result.imageSize()).isEqualTo("1K"); // not set in the override, so original value is kept
        assertThat(result.cachedContentName()).isEqualTo("cachedContents/override");
    }

    @Test
    void overrideWith_should_keep_gemini_specific_parameters_when_overriding_with_common_parameters() {
        GoogleAiGeminiChatRequestParameters original = GoogleAiGeminiChatRequestParameters.builder()
                .aspectRatio("16:9")
                .imageSize("2K")
                .cachedContentName("cachedContents/abc123")
                .build();

        GoogleAiGeminiChatRequestParameters result = original.overrideWith(
                DefaultChatRequestParameters.builder().temperature(0.1).build());

        assertThat(result.temperature()).isEqualTo(0.1);
        assertThat(result.aspectRatio()).isEqualTo("16:9");
        assertThat(result.imageSize()).isEqualTo("2K");
        assertThat(result.cachedContentName()).isEqualTo("cachedContents/abc123");
    }

    @Test
    void defaultedBy_should_apply_defaults_to_gemini_specific_parameters() {
        GoogleAiGeminiChatRequestParameters original = GoogleAiGeminiChatRequestParameters.builder()
                .cachedContentName("cachedContents/original")
                .build();

        GoogleAiGeminiChatRequestParameters fallback = GoogleAiGeminiChatRequestParameters.builder()
                .aspectRatio("1:1") // should be defaulted from fallback
                .imageSize("4K") // should be defaulted from fallback
                .cachedContentName("cachedContents/fallback") // should be ignored as original has it
                .build();

        GoogleAiGeminiChatRequestParameters result = original.defaultedBy(fallback);

        assertThat(result.aspectRatio()).isEqualTo("1:1");
        assertThat(result.imageSize()).isEqualTo("4K");
        assertThat(result.cachedContentName()).isEqualTo("cachedContents/original");
    }

    @Test
    void defaultedBy_should_keep_parameters_when_defaulted_by_common_parameters() {
        GoogleAiGeminiChatRequestParameters original = GoogleAiGeminiChatRequestParameters.builder()
                .aspectRatio("16:9")
                .imageSize("2K")
                .cachedContentName("cachedContents/abc123")
                .build();

        GoogleAiGeminiChatRequestParameters result = original.defaultedBy(
                DefaultChatRequestParameters.builder().temperature(0.1).build());

        assertThat(result.temperature()).isEqualTo(0.1);
        assertThat(result.aspectRatio()).isEqualTo("16:9");
        assertThat(result.imageSize()).isEqualTo("2K");
        assertThat(result.cachedContentName()).isEqualTo("cachedContents/abc123");
    }

    @Test
    void equals_and_hashCode() {
        GoogleAiGeminiChatRequestParameters one = GoogleAiGeminiChatRequestParameters.builder()
                .temperature(0.7)
                .aspectRatio("16:9")
                .imageSize("2K")
                .cachedContentName("cachedContents/abc123")
                .build();
        GoogleAiGeminiChatRequestParameters two = GoogleAiGeminiChatRequestParameters.builder()
                .temperature(0.7)
                .aspectRatio("16:9")
                .imageSize("2K")
                .cachedContentName("cachedContents/abc123")
                .build();
        GoogleAiGeminiChatRequestParameters differentAspectRatio = GoogleAiGeminiChatRequestParameters.builder()
                .temperature(0.7)
                .aspectRatio("1:1")
                .imageSize("2K")
                .cachedContentName("cachedContents/abc123")
                .build();
        GoogleAiGeminiChatRequestParameters differentTemperature = GoogleAiGeminiChatRequestParameters.builder()
                .temperature(0.3)
                .aspectRatio("16:9")
                .imageSize("2K")
                .cachedContentName("cachedContents/abc123")
                .build();

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
        assertThat(one).isNotEqualTo(differentAspectRatio);
        assertThat(one).isNotEqualTo(differentTemperature);
    }
}
