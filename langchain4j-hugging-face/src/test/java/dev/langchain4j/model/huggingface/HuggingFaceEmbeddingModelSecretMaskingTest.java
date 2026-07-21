package dev.langchain4j.model.huggingface;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HuggingFaceEmbeddingModelSecretMaskingTest {

    @Test
    void builder_toString_should_mask_access_token() {
        String toString =
                HuggingFaceEmbeddingModel.builder().accessToken("secret-token").toString();

        assertThat(toString).doesNotContain("secret-token").contains("accessToken=********");
    }

    @Test
    void builder_toString_should_render_null_access_token_as_null() {
        String toString = HuggingFaceEmbeddingModel.builder().toString();

        assertThat(toString).contains("accessToken=null");
    }
}
