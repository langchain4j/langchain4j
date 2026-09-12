package dev.langchain4j.mcp.client.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded to avoid races with the otherwise parallel test suite.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class McpLogLevelTest {

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ"})
    void should_parse_log_level_independently_of_default_locale(String languageTag) {
        withDefaultLocale(
                languageTag, () -> assertThat(McpLogLevel.from("info")).isEqualTo(McpLogLevel.INFO));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown"})
    void should_return_null_for_invalid_value(String value) {
        assertThat(McpLogLevel.from(value)).isNull();
    }

    private static void withDefaultLocale(String languageTag, Runnable action) {
        Locale previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            action.run();
        } finally {
            Locale.setDefault(previousDefault);
        }
    }
}
