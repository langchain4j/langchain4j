package dev.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoggingChatModelListenerTest {

    @Test
    void formatArguments() {
        assertThat(LoggingChatModelListener.formatArguments("""
                {
                    "arg0": 0
                }
                """)).isEqualTo("0");

        assertThat(LoggingChatModelListener.formatArguments("""
                {
                    "arg0": 0,
                    "arg1": 1
                }
                """)).isEqualTo("0, 1");

        assertThat(LoggingChatModelListener.formatArguments("""
                {
                    "a": 0
                }
                """)).isEqualTo("0");

        assertThat(LoggingChatModelListener.formatArguments("""
                {
                    "a": 0,
                    "b": 1
                }
                """)).isEqualTo("{\"a\":0,\"b\":1}");
    }
}
