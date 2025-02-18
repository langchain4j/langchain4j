package dev.langchain4j.exception;

import dev.langchain4j.service.IllegalConfigurationException;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class IllegalConfigurationExceptionTest implements WithAssertions {
    @Test
    void constructors() {
        assertThat(new IllegalConfigurationException("message abc 123"))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessage("message abc 123");

        assertThat(IllegalConfigurationException.illegalConfiguration("message abc 123"))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessage("message abc 123");

        assertThat(IllegalConfigurationException.illegalConfiguration("message %s %d", "abc", 123))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessage("message abc 123");
    }
}
