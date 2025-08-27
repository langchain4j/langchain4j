package dev.langchain4j.data.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AudioTest {

    @Test
    void should_create_audio_with_binary_data() {
        // given
        byte[] binaryData = "test audio data".getBytes();
        String mimeType = "audio/wav";

        // when
        Audio audio = Audio.builder().binaryData(binaryData).mimeType(mimeType).build();

        // then
        assertThat(audio.binaryData()).isEqualTo(binaryData);
        assertThat(audio.mimeType()).isEqualTo(mimeType);
        assertThat(audio.base64Data()).isNull();
        assertThat(audio.url()).isNull();
    }

    @Test
    void should_create_audio_with_base64_data() {
        // given
        String base64Data = Base64.getEncoder().encodeToString("test audio data".getBytes());
        String mimeType = "audio/mp3";

        // when
        Audio audio = Audio.builder().base64Data(base64Data).mimeType(mimeType).build();

        // then
        assertThat(audio.base64Data()).isEqualTo(base64Data);
        assertThat(audio.mimeType()).isEqualTo(mimeType);
        assertThat(audio.binaryData()).isNull();
        assertThat(audio.url()).isNull();
    }

    @Test
    void should_create_audio_with_url() {
        // given
        String urlString = "https://example.com/audio.wav";
        String mimeType = "audio/wav";

        // when
        Audio audio = Audio.builder().url(urlString).mimeType(mimeType).build();

        // then
        assertThat(audio.url()).isEqualTo(URI.create(urlString));
        assertThat(audio.mimeType()).isEqualTo(mimeType);
        assertThat(audio.binaryData()).isNull();
        assertThat(audio.base64Data()).isNull();
    }

    @Test
    void should_create_audio_with_uri() {
        // given
        URI uri = URI.create("https://example.com/audio.wav");
        String mimeType = "audio/wav";

        // when
        Audio audio = Audio.builder().url(uri).mimeType(mimeType).build();

        // then
        assertThat(audio.url()).isEqualTo(uri);
        assertThat(audio.mimeType()).isEqualTo(mimeType);
        assertThat(audio.binaryData()).isNull();
        assertThat(audio.base64Data()).isNull();
    }

    @Test
    void should_be_equal_when_same_content() {
        // given
        byte[] data = "test".getBytes();
        Audio audio1 = Audio.builder().binaryData(data).mimeType("audio/wav").build();
        Audio audio2 = Audio.builder().binaryData(data).mimeType("audio/wav").build();

        // then
        assertThat(audio1).isEqualTo(audio2);
        assertThat(audio1.hashCode()).isEqualTo(audio2.hashCode());
    }

    @Test
    void should_not_be_equal_when_different_content() {
        // given
        Audio audio1 = Audio.builder()
                .binaryData("test1".getBytes())
                .mimeType("audio/wav")
                .build();
        Audio audio2 = Audio.builder()
                .binaryData("test2".getBytes())
                .mimeType("audio/wav")
                .build();

        // then
        assertThat(audio1).isNotEqualTo(audio2);
    }

    @Test
    void should_not_be_equal_to_null() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test".getBytes())
                .mimeType("audio/wav")
                .build();

        // then
        assertThat(audio).isNotEqualTo(null);
    }

    @Test
    void should_not_be_equal_to_different_class() {
        // given
        Audio audio = Audio.builder()
                .binaryData("test".getBytes())
                .mimeType("audio/wav")
                .build();

        // then
        assertThat(audio).isNotEqualTo("not an audio");
    }

    @Test
    void should_have_proper_toString() {
        // given
        Audio audio = Audio.builder()
                .url("https://example.com/audio.wav")
                .base64Data("dGVzdA==")
                .mimeType("audio/wav")
                .build();

        // when
        String toString = audio.toString();

        // then
        assertThat(toString).contains("Audio");
        assertThat(toString).contains("url");
        assertThat(toString).contains("base64Data");
        assertThat(toString).contains("mimeType");
    }

    @Test
    void should_handle_file_protocol_url() {
        // given
        Audio audio = Audio.builder()
                .url("file:///home/user/audio.wav")
                .mimeType("audio/wav")
                .build();

        // then
        assertThat(audio.url().toString()).isEqualTo("file:///home/user/audio.wav");
        assertThat(audio.mimeType()).isEqualTo("audio/wav");
    }
}
