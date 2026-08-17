package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.UnsupportedFeatureException;
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

    @Test
    void should_set_image_detail_for_url_image() {
        UserMessage message =
                UserMessage.from(ImageContent.from("https://example.com/image.png", ImageContent.DetailLevel.HIGH));

        ChatCompletionMessageParam param = InternalOpenAiOfficialHelper.toOpenAiMessage(message);

        ChatCompletionContentPartImage.ImageUrl imageUrl = param.asUser()
                .content()
                .asArrayOfContentParts()
                .get(0)
                .asImageUrl()
                .imageUrl();
        assertThat(imageUrl.url()).isEqualTo("https://example.com/image.png");
        assertThat(imageUrl.detail()).contains(ChatCompletionContentPartImage.ImageUrl.Detail.HIGH);
    }

    @Test
    void should_set_image_detail_for_base64_image() {
        UserMessage message = UserMessage.from(ImageContent.from("QUJD", "image/png", ImageContent.DetailLevel.LOW));

        ChatCompletionMessageParam param = InternalOpenAiOfficialHelper.toOpenAiMessage(message);

        ChatCompletionContentPartImage.ImageUrl imageUrl = param.asUser()
                .content()
                .asArrayOfContentParts()
                .get(0)
                .asImageUrl()
                .imageUrl();
        assertThat(imageUrl.url()).isEqualTo("data:image/png;base64,QUJD");
        assertThat(imageUrl.detail()).contains(ChatCompletionContentPartImage.ImageUrl.Detail.LOW);
    }

    @Test
    void should_throw_when_image_detail_is_not_supported_by_openai_chat_completions() {
        UserMessage message = UserMessage.from(
                ImageContent.from("https://example.com/image.png", ImageContent.DetailLevel.ULTRA_HIGH));

        assertThatThrownBy(() -> InternalOpenAiOfficialHelper.toOpenAiMessage(message))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("ULTRA_HIGH")
                .hasMessageContaining("LOW, HIGH, AUTO");
    }

    @Test
    void should_throw_content_filtered_exception_when_chat_completion_contains_refusal() throws Exception {
        ChatCompletion chatCompletion = chatCompletionFrom("""
                {"role":"assistant","content":null,"refusal":"I cannot help with that request."}""");

        assertThatThrownBy(() -> InternalOpenAiOfficialHelper.aiMessageFrom(chatCompletion))
                .isInstanceOf(ContentFilteredException.class)
                .hasMessage("I cannot help with that request.");
    }

    @Test
    void should_not_throw_when_chat_completion_has_no_refusal() throws Exception {
        ChatCompletion chatCompletion = chatCompletionFrom("""
                {"role":"assistant","content":"Paris"}""");

        AiMessage aiMessage = InternalOpenAiOfficialHelper.aiMessageFrom(chatCompletion);

        assertThat(aiMessage.text()).isEqualTo("Paris");
    }

    @Test
    void should_not_throw_when_chat_completion_refusal_is_blank() throws Exception {
        ChatCompletion chatCompletion = chatCompletionFrom("""
                {"role":"assistant","content":"Paris","refusal":"  "}""");

        AiMessage aiMessage = InternalOpenAiOfficialHelper.aiMessageFrom(chatCompletion);

        assertThat(aiMessage.text()).isEqualTo("Paris");
    }

    private static ChatCompletion chatCompletionFrom(String assistantMessageJson) throws Exception {
        String json = """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1730000000,
                  "model": "gpt-4o-mini",
                  "choices": [
                    {"index": 0, "finish_reason": "stop", "logprobs": null, "message": %s}
                  ]
                }""".formatted(assistantMessageJson);
        return ObjectMappers.jsonMapper().readValue(json, ChatCompletion.class);
    }
}
