package dev.langchain4j.http.client.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * These tests mutate the {@link LoggingHttpClient#LOG_PAYLOADS_PROPERTY} system property,
 * so the class runs {@link Isolated} and single-threaded.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class LoggingHttpClientLogPayloadsPropertyTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(LoggingHttpClient.LOG_PAYLOADS_PROPERTY);
    }

    @Test
    void resolveLogPayloads_uses_system_property_when_constructor_argument_is_null() {
        System.clearProperty(LoggingHttpClient.LOG_PAYLOADS_PROPERTY);
        assertThat(LoggingHttpClient.resolveLogPayloads(null)).isFalse();

        System.setProperty(LoggingHttpClient.LOG_PAYLOADS_PROPERTY, "true");
        assertThat(LoggingHttpClient.resolveLogPayloads(null)).isTrue();
        assertThat(LoggingHttpClient.resolveLogPayloads(false)).isFalse();
        assertThat(LoggingHttpClient.resolveLogPayloads(true)).isTrue();

        System.setProperty(LoggingHttpClient.LOG_PAYLOADS_PROPERTY, "false");
        assertThat(LoggingHttpClient.resolveLogPayloads(null)).isFalse();
        assertThat(LoggingHttpClient.resolveLogPayloads(true)).isTrue();
    }
}
