package dev.langchain4j.service.tool.search.simple;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests mutate the JVM default {@link Locale}, so the whole class runs {@link Isolated}
 * and single-threaded to avoid races with the otherwise parallel test suite.
 * <p>
 * {@code tr} and {@code az} lowercase {@code 'I'} to the dotless {@code 'ı'} (U+0131),
 * {@code lt} applies its own dot rules, and {@code en-US} is the baseline.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class SimpleToolSearchStrategyLocaleTest {

    private static final ToolSpecification LIST_INVOICES = ToolSpecification.builder()
            .name("listInvoices")
            .description("Returns all Invoices for a customer")
            .build();

    private final SimpleToolSearchStrategy strategy =
            SimpleToolSearchStrategy.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void score_should_be_locale_independent(String languageTag) {
        // Without Locale.ROOT, "listInvoices".toLowerCase() yields "listınvoices" under tr/az,
        // which no longer contains the term "invoices", so the tool scores 0 and is dropped.
        withDefaultLocale(languageTag, () -> {
            int score = strategy.score(LIST_INVOICES, List.of("invoices"));

            // name (+2) + description (+1)
            assertThat(score).isEqualTo(3);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"tr-TR", "az-AZ", "lt-LT", "en-US"})
    void dotless_term_should_not_match_locale_independently(String languageTag) {
        // The mirror image: without Locale.ROOT this term matches under tr/az (score 3),
        // because the tool name and the term are both folded to the dotless form.
        withDefaultLocale(languageTag, () -> {
            int score = strategy.score(LIST_INVOICES, List.of("ınvoices"));

            assertThat(score).isZero();
        });
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
