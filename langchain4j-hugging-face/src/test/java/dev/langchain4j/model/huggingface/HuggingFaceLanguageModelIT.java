package dev.langchain4j.model.huggingface;

import static dev.langchain4j.model.huggingface.HuggingFaceModelName.MICROSOFT_PHI3_MINI_4K_INSTRUCT;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@EnabledIfEnvironmentVariable(named = "HF_API_KEY", matches = ".+")
class HuggingFaceLanguageModelIT {

    @Test
    void should_send_prompt_and_receive_response() {

        HuggingFaceLanguageModel model = HuggingFaceLanguageModel.builder()
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId(MICROSOFT_PHI3_MINI_4K_INSTRUCT)
                .timeout(ofSeconds(15))
                .temperature(0.7)
                .maxNewTokens(20)
                .waitForModel(true)
                .build();

        String answer = model.generate("What is the capital of the USA?").content();

        assertThat(answer).containsIgnoringCase("Washington");
    }

    @Test
    void custom_url_should_send_prompt_and_receive_response() {

        HuggingFaceLanguageModel model = HuggingFaceLanguageModel.builder()
                .baseUrl("https://api-inference.huggingface.co/")
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId(MICROSOFT_PHI3_MINI_4K_INSTRUCT)
                .timeout(ofSeconds(15))
                .temperature(0.7)
                .maxNewTokens(20)
                .waitForModel(true)
                .build();

        String answer = model.generate("What is the capital of the USA?").content();

        assertThat(answer).containsIgnoringCase("Washington");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_fail_when_access_token_is_null_or_empty(String accessToken) {

        assertThatThrownBy(() -> HuggingFaceLanguageModel.withAccessToken(accessToken))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
    }

    @Test
    void should_fail_when_baseUrl_is_not_valid() {
        assertThatThrownBy(() -> {
                    HuggingFaceLanguageModel.builder()
                            .baseUrl("//notValid/")
                            .accessToken(System.getenv("HF_API_KEY"))
                            .build();
                })
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected URL scheme 'http' or 'https'");
    }
}
