package dev.langchain4j.model.huggingface;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.huggingface.HuggingFaceModelName.MICROSOFT_PHI3_MINI_4K_INSTRUCT;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@EnabledIfEnvironmentVariable(named = "HF_API_KEY", matches = ".+")
class HuggingFaceChatModelIT {

    @Test
    void should_send_messages_and_receive_response() {

        HuggingFaceChatModel model = HuggingFaceChatModel.builder()
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId("microsoft/Phi-3.5-mini-instruct")
                .timeout(ofSeconds(60))
                .temperature(0.7)
                .maxNewTokens(20)
                .waitForModel(true)
                .build();

        AiMessage aiMessage = model.chat(
                        systemMessage("You are a good friend of mine, who likes to answer with jokes"),
                        userMessage("Hey Bro, what are you doing?"))
                .aiMessage();

        assertThat(aiMessage.text()).isNotBlank();
    }

    @Test
    void model_with_base_url_should_send_messages_and_receive_response() {

        HuggingFaceChatModel model = HuggingFaceChatModel.builder()
                .baseUrl("https://api-inference.huggingface.co/")
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId("microsoft/Phi-3.5-mini-instruct")
                .timeout(ofSeconds(60))
                .temperature(0.7)
                .maxNewTokens(20)
                .waitForModel(true)
                .build();

        AiMessage aiMessage = model.chat(
                        systemMessage("You are a good friend of mine, who likes to answer with jokes"),
                        userMessage("Hey Bro, what are you doing?"))
                .aiMessage();

        assertThat(aiMessage.text()).isNotBlank();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_fail_when_access_token_is_null_or_empty(String accessToken) {

        assertThatThrownBy(() -> HuggingFaceChatModel.withAccessToken(accessToken))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
    }

    @Test
    void should_fail_when_baseUrl_is_not_valid() {
        assertThatThrownBy(() -> {
                    HuggingFaceChatModel.builder()
                            .baseUrl("//not-valid-base-url/")
                            .accessToken(System.getenv("HF_API_KEY"))
                            .modelId(MICROSOFT_PHI3_MINI_4K_INSTRUCT)
                            .timeout(ofSeconds(15))
                            .temperature(0.7)
                            .maxNewTokens(20)
                            .waitForModel(true)
                            .build();
                })
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected URL scheme 'http' or 'https'");
    }
}
