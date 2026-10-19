package dev.langchain4j.store.embedding.filter.parser.sql;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.FilterParser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded to avoid races with the otherwise parallel test suite.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class SqlFilterParserLocaleTest {

    Clock clock = Clock.fixed(Instant.now(), UTC);
    FilterParser parser = new SqlFilterParser(clock);

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    void should_support_EXTRACT_independently_of_default_locale(String languageTag) {
        withDefaultLocale(
                languageTag,
                () -> assertThat(parser.parse("minute = EXTRACT(minute FROM CURRENT_TIMESTAMP)"))
                        .isEqualTo(metadataKey("minute").isEqualTo(currentMinute())));
    }

    private long currentMinute() {
        return LocalTime.now(clock).getMinute();
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
