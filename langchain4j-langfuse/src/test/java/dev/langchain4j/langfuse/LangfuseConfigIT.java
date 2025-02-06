package dev.langchain4j.langfuse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LangfuseConfigIT {

    @Test
    void shouldBuildConfigWithDefaultValues() {
        // When
        LangfuseConfig config = LangfuseConfig.builder()
                .publicKey("pub-key")
                .secretKey("secret-key")
                .build();

        // Then
        assertThat(config.getPublicKey()).isEqualTo("pub-key");
        assertThat(config.getSecretKey()).isEqualTo("secret-key");
        assertThat(config.getEndpoint()).isEqualTo("https://cloud.langfuse.com");
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void shouldBuildConfigWithCustomValues() {
        // When
        LangfuseConfig config = LangfuseConfig.builder()
                .publicKey("pub-key")
                .secretKey("secret-key")
                .endpoint("https://custom.endpoint")
                .enabled(false)
                .build();

        // Then
        assertThat(config.getEndpoint()).isEqualTo("https://custom.endpoint");
        assertThat(config.getPublicKey()).isEqualTo("pub-key");
        assertThat(config.getSecretKey()).isEqualTo("secret-key");
        assertThat(config.isEnabled()).isFalse();
    }
}
