package dev.langchain4j.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ToolErrorVisibleToLlmTest {

    @Test
    void of_should_create_an_exception_carrying_the_message_to_the_llm() {

        ToolErrorVisibleToLlmException exception = ToolErrorVisibleToLlm.of("There is no order with this ID.");

        assertThat(exception.messageForLlm()).isEqualTo("There is no order with this ID.");
        assertThat(exception.getMessage()).isEqualTo("There is no order with this ID.");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void of_should_keep_the_cause_without_exposing_it_to_the_llm() {

        Throwable cause = new IllegalStateException("jdbc:postgresql://db:5432/prod?password=hunter2");

        ToolErrorVisibleToLlmException exception =
                ToolErrorVisibleToLlm.of("The order database is temporarily unavailable.", cause);

        assertThat(exception.messageForLlm()).isEqualTo("The order database is temporarily unavailable.");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void of_should_reject_a_blank_message(String message) {

        assertThatThrownBy(() -> ToolErrorVisibleToLlm.of(message)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolErrorVisibleToLlm.of(message, new RuntimeException()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
