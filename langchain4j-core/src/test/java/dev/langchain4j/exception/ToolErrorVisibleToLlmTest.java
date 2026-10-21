package dev.langchain4j.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ToolErrorVisibleToLlmTest {

    @Test
    void from_should_create_an_exception_carrying_the_message_to_the_llm() {

        ToolErrorVisibleToLlmException exception = ToolErrorVisibleToLlm.from("There is no order with this ID.");

        assertThat(exception.messageForLlm()).isEqualTo("There is no order with this ID.");
        assertThat(exception.getMessage()).isEqualTo("There is no order with this ID.");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void from_should_keep_the_cause_without_exposing_it_to_the_llm() {

        Throwable cause = new IllegalStateException("jdbc:postgresql://db:5432/prod?password=hunter2");

        ToolErrorVisibleToLlmException exception =
                ToolErrorVisibleToLlm.from("The order database is temporarily unavailable.", cause);

        assertThat(exception.messageForLlm()).isEqualTo("The order database is temporarily unavailable.");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void from_should_reject_a_blank_message(String message) {

        assertThatThrownBy(() -> ToolErrorVisibleToLlm.from(message)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolErrorVisibleToLlm.from(message, new RuntimeException()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void llm_visible_tool_execution_exception_should_carry_its_message_to_the_llm() {

        LlmVisibleToolExecutionException exception =
                new LlmVisibleToolExecutionException("Missing required tool argument 'query'");

        assertThat(exception).isInstanceOf(ToolExecutionException.class).isInstanceOf(ToolErrorVisibleToLlm.class);
        assertThat(exception.messageForLlm()).isEqualTo("Missing required tool argument 'query'");
        assertThat(exception.getCause())
                .as("no synthetic cause, unlike ToolExecutionException(String)")
                .isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void llm_visible_tool_execution_exception_should_reject_a_blank_message(String message) {

        assertThatThrownBy(() -> new LlmVisibleToolExecutionException(message))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
