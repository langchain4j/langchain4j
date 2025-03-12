package dev.langchain4j.model.jlama;

import dev.langchain4j.exception.AuthenticationException;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JlamaUnexistingChatModelIT {

    @Test
    void should_fail_trying_to_download_unexisting_model() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        assertThatThrownBy(() -> JlamaChatModel.builder()
                .modelName("tjake/XYZ-JQ4")
                .modelCachePath(tmpDir.toPath())
                .temperature(0.0f)
                .maxTokens(64)
                .build()).isExactlyInstanceOf(AuthenticationException.class);
    }
}
