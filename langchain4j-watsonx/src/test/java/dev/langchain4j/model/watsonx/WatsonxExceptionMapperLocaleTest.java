package dev.langchain4j.model.watsonx;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.core.exception.model.WatsonxError;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated // mutates the JVM-wide default locale
class WatsonxExceptionMapperLocaleTest {

    private Locale defaultLocale;

    @BeforeEach
    void saveDefaultLocale() {
        defaultLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(defaultLocale);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    void should_map_known_error_code_independently_of_default_locale(String languageTag) {
        Locale.setDefault(Locale.forLanguageTag(languageTag));
        // Deliberately use a status whose fallback differs from the known error-code mapping.
        var details = new WatsonxError(
                500,
                "transaction-id",
                List.of(new WatsonxError.Error("invalid_input_argument", "invalid input", null)));
        var exception = new WatsonxException("invalid_input_argument", 500, details);

        var mapped = WatsonxExceptionMapper.INSTANCE.mapException(exception);

        assertThat(mapped)
                .isExactlyInstanceOf(InvalidRequestException.class)
                .hasMessage("invalid input")
                .hasCause(exception);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    void should_preserve_unknown_error_code_fallback(String languageTag) {
        Locale.setDefault(Locale.forLanguageTag(languageTag));
        var details = new WatsonxError(
                500, "transaction-id", List.of(new WatsonxError.Error("unknown_service_error", "service error", null)));
        var exception = new WatsonxException("unknown_service_error", 500, details);

        var mapped = WatsonxExceptionMapper.INSTANCE.mapException(exception);

        assertThat(mapped)
                .isExactlyInstanceOf(InternalServerException.class)
                .hasMessage("service error")
                .hasCause(exception);
    }
}
