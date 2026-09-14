package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.core.exception.model.WatsonxError;
import com.ibm.watsonx.ai.core.exception.model.WatsonxError.Code;
import dev.langchain4j.exception.InvalidRequestException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class WatsonxExceptionMapperLocaleTest {

    private static final WatsonxExceptionMapper mapper = WatsonxExceptionMapper.INSTANCE;

    private Locale defaultLocale;

    @BeforeEach
    void setTurkishLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void should_map_error_code_independently_of_default_locale() {
        var details = new WatsonxError(
                500,
                "96d1304b909f98de10f1199e92d9b873",
                List.of(new WatsonxError.Error(Code.INVALID_INPUT_ARGUMENT.value(), "invalid input argument", null)));
        var ex = mapper.mapException(new WatsonxException(Code.INVALID_INPUT_ARGUMENT.value(), 500, details));
        // before the fix: under tr-TR, toUpperCase() produces "INVALİD_...", lookup fails and falls back to
        // mapUnknownErrorCode(500) -> InternalServerException
        assertInstanceOf(InvalidRequestException.class, ex);
    }
}
