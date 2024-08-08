package dev.langchain4j.web.search.searchapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine.Locale;

class LocaleTest {

    @ParameterizedTest
    @NullAndEmptySource
    void testLocaleDefault(String input) {
    	Locale locale = new Locale(input);
    	assertThat(locale.getHl()).isEqualTo("en");
    	assertThat(locale.getGl()).isEqualTo("us");
    }
	
    @ParameterizedTest
    @CsvSource(value = {"  :en", "en:en", "en-ca:en", "en-gb:en", "es_ES:es", "es_MX:es", "zh_cn:zh", "zh_CN:zh", "*:en"}, delimiter = ':')
    void testLocalePrefix(String input, String expected) {
    	Locale locale = new Locale(input);
    	assertThat(locale.getHl()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"  :us", "en:us", "en-ca:ca", "en-gb:gb", "es_ES:es", "es_MX:mx", "zh_cn:cn", "zh_CN:cn", "*:us"}, delimiter = ':')
    void testLocaleSuffix(String input, String expected) {
    	Locale locale = new Locale(input);
    	assertThat(locale.getGl()).isEqualTo(expected);
    }
}
