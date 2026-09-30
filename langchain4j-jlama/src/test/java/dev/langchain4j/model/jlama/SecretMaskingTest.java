package dev.langchain4j.model.jlama;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretMaskingTest {

    @Test
    void chatModelBuilder_toString_should_mask_auth_token() {
        String toString = JlamaChatModel.builder().authToken("secret-token").toString();

        assertThat(toString).doesNotContain("secret-token").contains("authToken=********");
    }

    @Test
    void chatModelBuilder_toString_should_render_null_auth_token_as_null() {
        assertThat(JlamaChatModel.builder().toString()).contains("authToken=null");
    }

    @Test
    void streamingChatModelBuilder_toString_should_mask_auth_token() {
        String toString =
                JlamaStreamingChatModel.builder().authToken("secret-token").toString();

        assertThat(toString).doesNotContain("secret-token").contains("authToken=********");
    }

    @Test
    void streamingChatModelBuilder_toString_should_render_null_auth_token_as_null() {
        assertThat(JlamaStreamingChatModel.builder().toString()).contains("authToken=null");
    }

    @Test
    void languageModelBuilder_toString_should_mask_auth_token() {
        String toString = JlamaLanguageModel.builder().authToken("secret-token").toString();

        assertThat(toString).doesNotContain("secret-token").contains("authToken=********");
    }

    @Test
    void languageModelBuilder_toString_should_render_null_auth_token_as_null() {
        assertThat(JlamaLanguageModel.builder().toString()).contains("authToken=null");
    }

    @Test
    void streamingLanguageModelBuilder_toString_should_mask_auth_token() {
        String toString =
                JlamaStreamingLanguageModel.builder().authToken("secret-token").toString();

        assertThat(toString).doesNotContain("secret-token").contains("authToken=********");
    }

    @Test
    void streamingLanguageModelBuilder_toString_should_render_null_auth_token_as_null() {
        assertThat(JlamaStreamingLanguageModel.builder().toString()).contains("authToken=null");
    }

    @Test
    void embeddingModelBuilder_toString_should_mask_auth_token() {
        String toString = JlamaEmbeddingModel.builder().authToken("secret-token").toString();

        assertThat(toString).doesNotContain("secret-token").contains("authToken=********");
    }

    @Test
    void embeddingModelBuilder_toString_should_render_null_auth_token_as_null() {
        assertThat(JlamaEmbeddingModel.builder().toString()).contains("authToken=null");
    }
}
