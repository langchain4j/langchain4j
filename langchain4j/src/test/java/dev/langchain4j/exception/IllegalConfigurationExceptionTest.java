package dev.langchain4j.exception;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class IllegalConfigurationExceptionTest implements WithAssertions {
    @Test
    public void test_constructors() {
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