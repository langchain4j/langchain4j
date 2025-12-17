package dev.langchain4j.model.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelTypeTest {

    @Test
    void should_have_all_expected_types() {
        ModelType[] types = ModelType.values();

        assertThat(types)
                .containsExactlyInAnyOrder(
                        ModelType.CHAT,
                        ModelType.EMBEDDING,
                        ModelType.IMAGE_GENERATION,
                        ModelType.IMAGE_UNDERSTANDING,
                        ModelType.AUDIO_TRANSCRIPTION,
                        ModelType.AUDIO_GENERATION,
                        ModelType.VIDEO_UNDERSTANDING,
                        ModelType.MODERATION,
                        ModelType.CODE_COMPLETION,
                        ModelType.RERANKING,
                        ModelType.OTHER);
    }

    @Test
    void should_convert_to_string() {
        assertThat(ModelType.CHAT.toString()).isEqualTo("CHAT");
        assertThat(ModelType.EMBEDDING.toString()).isEqualTo("EMBEDDING");
        assertThat(ModelType.IMAGE_GENERATION.toString()).isEqualTo("IMAGE_GENERATION");
    }
}
