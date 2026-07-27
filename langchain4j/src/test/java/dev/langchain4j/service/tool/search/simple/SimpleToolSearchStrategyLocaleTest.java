package dev.langchain4j.service.tool.search.simple;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
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
class SimpleToolSearchStrategyLocaleTest {

    private final SimpleToolSearchStrategy strategy =
            SimpleToolSearchStrategy.builder().build();

    private static final ToolSpecification LIST_INVOICES = ToolSpecification.builder()
            .name("listInvoices")
            .description("Returns all Invoices for a customer")
            .build();

    @Test
    void score_should_be_locale_independent() {
        // In the Turkish locale, "listInvoices".toLowerCase() yields "listınvoices" (dotless i),
        // which no longer contains the term "invoices".
        withDefaultLocale(Locale.forLanguageTag("tr-TR"), () -> {
            int score = strategy.score(LIST_INVOICES, List.of("invoices"));

            // name (+2) + description (+1)
            assertThat(score).isEqualTo(3);
        });
    }

    @Test
    void score_should_be_zero_for_non_matching_term_in_turkish_locale() {
        withDefaultLocale(Locale.forLanguageTag("tr-TR"), () -> {
            int score = strategy.score(LIST_INVOICES, List.of("weather"));

            assertThat(score).isZero();
        });
    }

    private static void withDefaultLocale(Locale locale, Runnable action) {
        Locale previousDefault = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            action.run();
        } finally {
            Locale.setDefault(previousDefault);
        }
    }
}
