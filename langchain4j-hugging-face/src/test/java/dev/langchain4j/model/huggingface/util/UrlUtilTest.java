package dev.langchain4j.model.huggingface.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlUtilTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://a5q6s8ormylmu6y.us-east-1.aws.endpoints.huggingface.cloud",
                "http://valid-url.com",
                "http://example.com/path?query=value#fragment"
            })
    void isNotValidUrlFalse(String url) {
        assertFalse(UrlUtil.isNotValidUrl(url));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://///a5q6s8ormylmu6y.us-east-1.aws.endpoints.huggingface.cloud",
                "htp://valid-url.com",
                "//example.com/path?query=value#fragment"
            })
    void isValidUrlFalse(String url) {
        assertFalse(UrlUtil.isValidUrl(url));
    }
}
