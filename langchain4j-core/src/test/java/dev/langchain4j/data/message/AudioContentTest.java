package dev.langchain4j.data.message;

import dev.langchain4j.data.audio.Audio;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class AudioContentTest {
    @Test
    void test_methods() {
        Audio urlAudio = Audio.builder()
            .url(URI.create("https://example.com/sound.mp3"))
            .build();
        AudioContent audioContent = new AudioContent(urlAudio);

        assertThat(audioContent.audio()).isEqualTo(urlAudio);
        assertThat(audioContent.type()).isEqualTo(ContentType.AUDIO);

        assertThat(audioContent)
            .hasToString(
                "AudioContent { " +
                    "audio = Audio { " +
                    "url = \"https://example.com/sound.mp3\", " +
                    "base64Data = null, mimeType = null } " +
                    "}");
    }

    @Test
    public void test_equals_hashCode() {
        AudioContent a1 = AudioContent.from("https://example.com/sound.mp3");
        AudioContent a2 = AudioContent.from("https://example.com/sound.mp3");

        AudioContent a3 = AudioContent.from("https://example.com/sound.wav");
        AudioContent a4 = AudioContent.from("https://example.com/sound.wav");

        assertThat(a1)
            .isEqualTo(a1)
            .isNotEqualTo(null)
            .isNotEqualTo(new Object())
            .isEqualTo(a2)
            .hasSameHashCodeAs(a2)
            .isNotEqualTo(a3)
            .isNotEqualTo(a4);

        assertThat(a3)
            .isEqualTo(a3)
            .isEqualTo(a4)
            .hasSameHashCodeAs(a4);
    }

    @Test
    public void test_builders() {
        Audio urlAudio = Audio.builder()
            .url(URI.create("https://example.com/sound.mp3"))
            .build();
        assertThat(new AudioContent(urlAudio))
            .isEqualTo(new AudioContent(urlAudio))
            .isEqualTo(AudioContent.from(urlAudio))
            .isEqualTo(AudioContent.from(urlAudio))
            .isEqualTo(new AudioContent(urlAudio.url()))
            .isEqualTo(new AudioContent(urlAudio.url().toString()))
            .isEqualTo(AudioContent.from(urlAudio.url()))
            .isEqualTo(AudioContent.from(urlAudio.url().toString()));

        Audio base64Audio = Audio.builder()
            .base64Data("YXVkaW8=")
            .mimeType("mimeType")
            .build();
        assertThat(new AudioContent(base64Audio))
            .isEqualTo(new AudioContent(base64Audio))
            .isEqualTo(AudioContent.from(base64Audio))
            .isEqualTo(new AudioContent(base64Audio.base64Data(), base64Audio.mimeType()))
            .isEqualTo(AudioContent.from(base64Audio.base64Data(), base64Audio.mimeType()));
    }
}
