package dev.langchain4j.model.huggingface;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static dev.langchain4j.model.huggingface.HuggingFaceModelName.TII_UAE_FALCON_7B_INSTRUCT;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HuggingFaceLanguageModelIT {

    @Test
    public void should_send_prompt_and_receive_response() {

        HuggingFaceLanguageModel model = HuggingFaceLanguageModel.builder()
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId(TII_UAE_FALCON_7B_INSTRUCT)
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
    public void should_fail_when_access_token_is_null_or_empty(String accessToken) {

        assertThatThrownBy(() -> HuggingFaceLanguageModel.withAccessToken(accessToken))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
    }
}