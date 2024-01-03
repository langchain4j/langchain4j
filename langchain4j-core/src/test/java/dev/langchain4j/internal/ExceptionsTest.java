package dev.langchain4j.internal;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ExceptionsTest implements WithAssertions {
    @Test
    public void test_illegalArgument() {
        assertThat(Exceptions.illegalArgument("test %s", "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("test test");
    }

    @Test
    public void test_runtime() {
        assertThat(Exceptions.runtime("test %s", "test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test test");
    }
}