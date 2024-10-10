package dev.langchain4j.model.jlama;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonProcessingExceptionTest {

    @Test
    void testConstructorWithMessageAndCause() {
        String errorMessage = "Test error message";
        Throwable cause = new RuntimeException("Original cause");

        JacksonProcessingException exception = new JacksonProcessingException(errorMessage, cause);

        assertThat(exception)
                .hasMessage(errorMessage)
                .hasCause(cause);
    }

    @Test
    void testExceptionInheritance() {
        JacksonProcessingException exception = new JacksonProcessingException("Test", null);

        assertThat(exception)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testExceptionWithNullCause() {
        String errorMessage = "Test error message";

        JacksonProcessingException exception = new JacksonProcessingException(errorMessage, null);

        assertThat(exception)
                .hasMessage(errorMessage)
                .hasNoCause();
    }

    @Test
    void testExceptionStackTrace() {
        String errorMessage = "Test error message";
        Throwable cause = new RuntimeException("Original cause");

        JacksonProcessingException exception = new JacksonProcessingException(errorMessage, cause);

        assertThat(exception.getStackTrace()).isNotEmpty();
    }
}