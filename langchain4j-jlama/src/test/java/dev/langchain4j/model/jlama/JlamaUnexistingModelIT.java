package dev.langchain4j.model.jlama;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AuthenticationException;
import java.io.File;
import org.junit.jupiter.api.Test;

class JlamaUnexistingModelIT {

    @Test
    void streaming_chat_model_should_fail_trying_to_download_unexisting_model() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        assertThatThrownBy(() -> JlamaStreamingChatModel.builder()
                        .modelName("tjake/XYZ-JQ4")
                        .modelCachePath(tmpDir.toPath())
                        .temperature(0.0f)
                        .maxTokens(64)
                        .build())
                .isExactlyInstanceOf(AuthenticationException.class);
    }

    @Test
    void language_model_should_fail_trying_to_download_unexisting_model() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        assertThatThrownBy(() -> JlamaLanguageModel.builder()
                        .modelName("tjake/XYZ-JQ4")
                        .modelCachePath(tmpDir.toPath())
                        .temperature(0.0f)
                        .maxTokens(64)
                        .build())
                .isExactlyInstanceOf(AuthenticationException.class);
    }

    @Test
    void streaming_language_model_should_fail_trying_to_download_unexisting_model() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        assertThatThrownBy(() -> JlamaStreamingLanguageModel.builder()
                        .modelName("tjake/XYZ-JQ4")
                        .modelCachePath(tmpDir.toPath())
                        .temperature(0.0f)
                        .maxTokens(64)
                        .build())
                .isExactlyInstanceOf(AuthenticationException.class);
    }

    @Test
    void embedding_model_should_fail_trying_to_download_unexisting_model() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        assertThatThrownBy(() -> JlamaEmbeddingModel.builder()
                        .modelName("tjake/XYZ-JQ4")
                        .modelCachePath(tmpDir.toPath())
                        .build())
                .isExactlyInstanceOf(AuthenticationException.class);
    }
}
