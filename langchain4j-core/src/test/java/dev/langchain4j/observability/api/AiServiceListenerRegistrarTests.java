package dev.langchain4j.observability.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiServiceListenerRegistrarTests {
    @Test
    void correctInstance() {
        assertThat(AiServiceListenerRegistrar.newInstance())
                .isNotNull()
                .isExactlyInstanceOf(DefaultAiServiceListenerRegistrar.class);
    }
}
