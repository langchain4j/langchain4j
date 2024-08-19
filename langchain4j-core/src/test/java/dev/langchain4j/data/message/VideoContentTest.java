package dev.langchain4j.data.message;

import dev.langchain4j.data.video.Video;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class VideoContentTest {
    @Test
    void test_methods() {
        Video urlVideo = Video.builder()
            .url(URI.create("https://example.com/video.mp4"))
            .build();
        VideoContent videoContent = new VideoContent(urlVideo);

        assertThat(videoContent.video()).isEqualTo(urlVideo);
        assertThat(videoContent.type()).isEqualTo(ContentType.VIDEO);

        assertThat(videoContent)
            .hasToString(
                "VideoContent { " +
                    "video = Video { " +
                    "url = \"https://example.com/video.mp4\", " +
                    "base64Data = null, mimeType = null } " +
                    "}");
    }

    @Test
    public void test_equals_hashCode() {
        VideoContent v1 = VideoContent.from("https://example.com/video.mp4");
        VideoContent v2 = VideoContent.from("https://example.com/video.mp4");

        VideoContent v3 = VideoContent.from("https://example.com/sound.wav");
        VideoContent v4 = VideoContent.from("https://example.com/sound.wav");

        assertThat(v1)
            .isEqualTo(v1)
            .isNotEqualTo(null)
            .isNotEqualTo(new Object())
            .isEqualTo(v2)
            .hasSameHashCodeAs(v2)
            .isNotEqualTo(v3)
            .isNotEqualTo(v4);

        assertThat(v3)
            .isEqualTo(v3)
            .isEqualTo(v4)
            .hasSameHashCodeAs(v4);
    }

    @Test
    public void test_builders() {
        Video urlVideo = Video.builder()
            .url(URI.create("https://example.com/video.mp4"))
            .build();
        assertThat(new VideoContent(urlVideo))
            .isEqualTo(new VideoContent(urlVideo))
            .isEqualTo(VideoContent.from(urlVideo))
            .isEqualTo(VideoContent.from(urlVideo))
            .isEqualTo(new VideoContent(urlVideo.url()))
            .isEqualTo(new VideoContent(urlVideo.url().toString()))
            .isEqualTo(VideoContent.from(urlVideo.url()))
            .isEqualTo(VideoContent.from(urlVideo.url().toString()));

        Video base64video = Video.builder()
            .base64Data("dmlkZW8=")
            .mimeType("mimeType")
            .build();
        assertThat(new VideoContent(base64video))
            .isEqualTo(new VideoContent(base64video))
            .isEqualTo(VideoContent.from(base64video))
            .isEqualTo(new VideoContent(base64video.base64Data(), base64video.mimeType()))
            .isEqualTo(VideoContent.from(base64video.base64Data(), base64video.mimeType()));
    }
}
