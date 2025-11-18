package dev.langchain4j.model.openai.internal.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VideoContentConversionTest {

    @Test
    void should_build_user_message_with_video_url() {
        // given
        String videoUrl = "https://example.com/video.mp4";

        // when
        UserMessage userMessage = UserMessage.builder()
                .addText("Describe this video")
                .addVideoUrl(videoUrl)
                .build();

        // then
        assertThat(userMessage.content()).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        java.util.List<Content> contents = (java.util.List<Content>) userMessage.content();
        
        assertThat(contents).hasSize(2);
        
        // First content should be text
        assertThat(contents.get(0).type()).isEqualTo(ContentType.TEXT);
        assertThat(contents.get(0).text()).isEqualTo("Describe this video");
        
        // Second content should be video
        assertThat(contents.get(1).type()).isEqualTo(ContentType.VIDEO_URL);
        assertThat(contents.get(1).videoUrl()).isNotNull();
        assertThat(contents.get(1).videoUrl().getUrl()).isEqualTo(videoUrl);
    }

    @Test
    void should_build_user_message_with_multiple_video_urls() {
        // given
        String videoUrl1 = "https://example.com/video1.mp4";
        String videoUrl2 = "https://example.com/video2.mp4";

        // when
        UserMessage userMessage = UserMessage.builder()
                .addVideoUrls(videoUrl1, videoUrl2)
                .build();

        // then
        @SuppressWarnings("unchecked")
        java.util.List<Content> contents = (java.util.List<Content>) userMessage.content();
        
        assertThat(contents).hasSize(2);
        assertThat(contents.get(0).type()).isEqualTo(ContentType.VIDEO_URL);
        assertThat(contents.get(0).videoUrl().getUrl()).isEqualTo(videoUrl1);
        assertThat(contents.get(1).type()).isEqualTo(ContentType.VIDEO_URL);
        assertThat(contents.get(1).videoUrl().getUrl()).isEqualTo(videoUrl2);
    }

    @Test
    void should_create_video_url_with_builder() {
        // given
        String url = "https://example.com/video.mp4";

        // when
        VideoUrl videoUrl = VideoUrl.builder()
                .url(url)
                .build();

        // then
        assertThat(videoUrl.getUrl()).isEqualTo(url);
    }

    @Test
    void should_have_correct_equals_and_hashcode_for_video_url() {
        // given
        String url = "https://example.com/video.mp4";
        VideoUrl videoUrl1 = VideoUrl.builder().url(url).build();
        VideoUrl videoUrl2 = VideoUrl.builder().url(url).build();
        VideoUrl videoUrl3 = VideoUrl.builder().url("https://example.com/other_video.mp4").build();

        // then
        assertThat(videoUrl1)
                .isEqualTo(videoUrl2)
                .hasSameHashCodeAs(videoUrl2)
                .isNotEqualTo(videoUrl3);
    }

    @Test
    void should_have_correct_tostring_for_video_url() {
        // given
        String url = "https://example.com/video.mp4";
        VideoUrl videoUrl = VideoUrl.builder().url(url).build();

        // when
        String toString = videoUrl.toString();

        // then
        assertThat(toString).contains("VideoUrl");
        assertThat(toString).contains(url);
    }

    @Test
    void should_include_video_in_content_equals_and_hashcode() {
        // given
        VideoUrl videoUrl = VideoUrl.builder().url("https://example.com/video.mp4").build();
        
        Content content1 = Content.builder()
                .type(ContentType.VIDEO_URL)
                .videoUrl(videoUrl)
                .build();
        
        Content content2 = Content.builder()
                .type(ContentType.VIDEO_URL)
                .videoUrl(videoUrl)
                .build();
        
        Content content3 = Content.builder()
                .type(ContentType.VIDEO_URL)
                .videoUrl(VideoUrl.builder().url("https://example.com/other_video.mp4").build())
                .build();

        // then
        assertThat(content1)
                .isEqualTo(content2)
                .hasSameHashCodeAs(content2)
                .isNotEqualTo(content3);
    }

    @Test
    void should_include_video_in_content_tostring() {
        // given
        VideoUrl videoUrl = VideoUrl.builder().url("https://example.com/video.mp4").build();
        Content content = Content.builder()
                .type(ContentType.VIDEO_URL)
                .videoUrl(videoUrl)
                .build();

        // when
        String toString = content.toString();

        // then
        assertThat(toString).contains("VIDEO_URL");
        assertThat(toString).contains("videoUrl");
    }
}

