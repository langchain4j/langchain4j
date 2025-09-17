package dev.langchain4j.audit.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiServiceInteractionEventListenerRegistrarTests {
    @Test
    void correctInstance() {
        assertThat(AiServiceInteractionEventListenerRegistrar.getInstance())
                .isNotNull()
                .isExactlyInstanceOf(DefaultAiServiceInteractionEventListenerRegistrar.class);
    }
}
