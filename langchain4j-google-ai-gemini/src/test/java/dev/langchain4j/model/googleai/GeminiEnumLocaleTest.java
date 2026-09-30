package dev.langchain4j.model.googleai;

import static dev.langchain4j.spi.ServiceHelper.loadFactory;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiCodeExecutionResult;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiCodeExecutionResult.GeminiOutcome;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiExecutableCode;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiExecutableCode.GeminiLanguage;
import dev.langchain4j.spi.json.ProviderJsonCodecFactory;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated // mutates the JVM-wide default locale
class GeminiEnumLocaleTest {

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
    void should_render_schema_types_independently_of_default_locale(String languageTag) {
        Locale.setDefault(Locale.forLanguageTag(languageTag));

        assertThat(GeminiType.values())
                .extracting(GeminiType::toString)
                .containsExactly("string", "number", "integer", "boolean", "array", "object", "null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    void should_render_languages_independently_of_default_locale(String languageTag) {
        Locale.setDefault(Locale.forLanguageTag(languageTag));

        assertThat(GeminiLanguage.values())
                .extracting(GeminiLanguage::toString)
                .containsExactly("python", "language_unspecified");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    void should_render_outcomes_independently_of_default_locale(String languageTag) {
        Locale.setDefault(Locale.forLanguageTag(languageTag));

        assertThat(GeminiOutcome.values())
                .extracting(GeminiOutcome::toString)
                .containsExactly("outcome_unspecified", "outcome_ok", "outcome_failed", "outcome_deadline_exceeded");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "tr-TR", "az-AZ"})
    void should_serialize_enums_independently_of_default_locale(String languageTag) {
        Locale.setDefault(Locale.forLanguageTag(languageTag));

        var spec = ProviderJsonSpec.builder()
                .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
                .prettyPrint(true)
                .build();
        // A fresh optional codec prevents enum strings cached under an earlier locale from masking the bug.
        var factory = loadFactory(ProviderJsonCodecFactory.class);
        var codec = factory == null ? ProviderJson.codec(spec) : factory.create(spec);
        var schema = GeminiSchema.builder().type(GeminiType.INTEGER).build();
        var code = new GeminiExecutableCode(GeminiLanguage.LANGUAGE_UNSPECIFIED, "print(1)");
        var result = new GeminiCodeExecutionResult(GeminiOutcome.OUTCOME_FAILED, "failed");

        // Jackson 2 uses enum names; the optional Jackson 3 codec uses toString().
        assertThat(codec.fromJson(codec.toJson(schema), Map.class).get("type")).isIn("INTEGER", "integer");
        assertThat(codec.fromJson(codec.toJson(code), Map.class).get("programmingLanguage"))
                .isIn("LANGUAGE_UNSPECIFIED", "language_unspecified");
        assertThat(codec.fromJson(codec.toJson(result), Map.class).get("outcome"))
                .isIn("OUTCOME_FAILED", "outcome_failed");
        assertThat(codec.fromJson(codec.toJson(GeminiType.INTEGER), GeminiType.class))
                .isEqualTo(GeminiType.INTEGER);
        assertThat(codec.fromJson(codec.toJson(code), GeminiExecutableCode.class))
                .isEqualTo(code);
        assertThat(codec.fromJson(codec.toJson(result), GeminiCodeExecutionResult.class))
                .isEqualTo(result);
    }
}
