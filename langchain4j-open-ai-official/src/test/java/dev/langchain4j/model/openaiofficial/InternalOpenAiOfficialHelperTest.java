package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

class InternalOpenAiOfficialHelperTest {

    private static final String BASE64_AUDIO = "QUJD"; // any non-blank base64 payload

    @Test
    void should_set_input_audio_format_from_wav_mime_type() {
        UserMessage message = UserMessage.from(AudioContent.from(BASE64_AUDIO, "audio/wav"));

        ChatCompletionMessageParam param = InternalOpenAiOfficialHelper.toOpenAiMessage(message);

        ChatCompletionContentPartInputAudio.InputAudio inputAudio = param.asUser()
                .content()
                .asArrayOfContentParts()
                .get(0)
                .asInputAudio()
                .inputAudio();
        assertThat(inputAudio.data()).isEqualTo(BASE64_AUDIO);
        assertThat(inputAudio.format()).isEqualTo(ChatCompletionContentPartInputAudio.InputAudio.Format.WAV);
    }

    @Test
    void should_set_input_audio_format_from_mp3_mime_type() {
        UserMessage message = UserMessage.from(AudioContent.from(BASE64_AUDIO, "audio/mp3"));

        ChatCompletionMessageParam param = InternalOpenAiOfficialHelper.toOpenAiMessage(message);

        assertThat(param.asUser()
                        .content()
                        .asArrayOfContentParts()
                        .get(0)
                        .asInputAudio()
                        .inputAudio()
                        .format())
                .isEqualTo(ChatCompletionContentPartInputAudio.InputAudio.Format.MP3);
    }
}
