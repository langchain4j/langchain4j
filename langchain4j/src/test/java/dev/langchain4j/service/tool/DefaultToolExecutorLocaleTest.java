package dev.langchain4j.service.tool;

import static dev.langchain4j.service.tool.DefaultToolExecutor.coerceArgument;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded to avoid races with the otherwise parallel test suite.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DefaultToolExecutorLocaleTest {

    public enum LocaleSensitiveEnum {
        ACTIVE,
        INACTIVE
    }

    @Test
    void coerce_argument_to_enum_should_be_locale_independent() {
        // In the Turkish locale, "active".toUpperCase() yields "ACTİVE" (dotted capital I),
        // which must still resolve to the enum constant ACTIVE.
        Locale previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(coerceArgument("active", "arg", LocaleSensitiveEnum.class, null))
                    .isEqualTo(LocaleSensitiveEnum.ACTIVE);
        } finally {
            Locale.setDefault(previousDefault);
        }
    }
}
