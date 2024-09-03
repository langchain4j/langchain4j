package dev.langchain4j.model.huggingface;

import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.huggingface.HuggingFaceModelName.TII_UAE_FALCON_7B_INSTRUCT;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HuggingFaceChatModelIT {

    @Test
    public void should_send_messages_and_receive_response() {

        HuggingFaceChatModel model = HuggingFaceChatModel.builder()
                .accessToken(System.getenv("HF_API_KEY"))
                .modelId(TII_UAE_FALCON_7B_INSTRUCT)
                .timeout(ofSeconds(15))
                .temperature(0.7)
                .maxNewTokens(20)
                .waitForModel(true)
                .build();

        AiMessage aiMessage = model.generate(
                systemMessage("You are a good friend of mine, who likes to answer with jokes"),
                userMessage("Hey Bro, what are you doing?")
        ).content();

        assertThat(aiMessage.text()).isNotBlank();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void should_fail_when_access_token_is_null_or_empty(String accessToken) {

        assertThatThrownBy(() -> HuggingFaceChatModel.withAccessToken(accessToken))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("HuggingFace access token must be defined. It can be generated here: https://huggingface.co/settings/tokens");
    }
}