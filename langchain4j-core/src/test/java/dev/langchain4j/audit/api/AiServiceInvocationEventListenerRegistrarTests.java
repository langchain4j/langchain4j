package dev.langchain4j.audit.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiServiceInvocationEventListenerRegistrarTests {
    @Test
    void correctInstance() {
        assertThat(AiServiceInvocationEventListenerRegistrar.getInstance())
                .isNotNull()
                .isExactlyInstanceOf(DefaultAiServiceInvocationEventListenerRegistrar.class);
    }
}
