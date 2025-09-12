package dev.langchain4j.audit.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LLMInteractionEventListenerRegistrarTests {
    @Test
    void correctInstance() {
        assertThat(LLMInteractionEventListenerRegistrar.getInstance())
                .isNotNull()
                .isExactlyInstanceOf(DefaultLLMInteractionEventListenerRegistrar.class);
    }
}
