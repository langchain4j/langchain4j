package dev.langchain4j.web.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSearchRequestTest {

    @Test
    void should_build_webSearchRequest_with_default_values(){
        WebSearchRequest webSearchRequest = WebSearchRequest.from("query");

        assertThat(webSearchRequest.searchTerms()).isEqualTo("query");
        assertThat(webSearchRequest.startPage()).isEqualTo(1);
        assertThat(webSearchRequest.maxResults()).isNull();
        assertThat(webSearchRequest.language()).isNull();
        assertThat(webSearchRequest.geoLocation()).isNull();
        assertThat(webSearchRequest.startIndex()).isNull();
        assertThat(webSearchRequest.safeSearch()).isTrue();
        assertThat(webSearchRequest.additionalParams()).isEmpty();

        assertThat(webSearchRequest).hasToString("WebSearchRequest{searchTerms='query', maxResults=null, language='null', geoLocation='null', startPage=1, startIndex=null, siteRestrict=true, additionalParams={}}");
    }

    @Test
    void should_build_webSearchRequest_with_default_values_builder(){
        WebSearchRequest webSearchRequest = WebSearchRequest.builder().searchTerms("query").build();

        assertThat(webSearchRequest.searchTerms()).isEqualTo("query");
        assertThat(webSearchRequest.startPage()).isEqualTo(1);
        assertThat(webSearchRequest.maxResults()).isNull();
        assertThat(webSearchRequest.language()).isNull();
        assertThat(webSearchRequest.geoLocation()).isNull();
        assertThat(webSearchRequest.startIndex()).isNull();
        assertThat(webSearchRequest.safeSearch()).isTrue();
        assertThat(webSearchRequest.additionalParams()).isEmpty();

        assertThat(webSearchRequest).hasToString("WebSearchRequest{searchTerms='query', maxResults=null, language='null', geoLocation='null', startPage=1, startIndex=null, siteRestrict=true, additionalParams={}}");
    }

    @Test
    void should_build_webSearchRequest_with_custom_maxResults(){
        WebSearchRequest webSearchRequest = WebSearchRequest.from("query", 10);

        assertThat(webSearchRequest.searchTerms()).isEqualTo("query");
        assertThat(webSearchRequest.startPage()).isEqualTo(1);
        assertThat(webSearchRequest.maxResults()).isEqualTo(10);
        assertThat(webSearchRequest.language()).isNull();
        assertThat(webSearchRequest.geoLocation()).isNull();
        assertThat(webSearchRequest.startIndex()).isNull();
        assertThat(webSearchRequest.safeSearch()).isTrue();
        assertThat(webSearchRequest.additionalParams()).isEmpty();

        assertThat(webSearchRequest).hasToString("WebSearchRequest{searchTerms='query', maxResults=10, language='null', geoLocation='null', startPage=1, startIndex=null, siteRestrict=true, additionalParams={}}");
    }

    @Test
    void should_build_webSearchRequest_with_custom_maxResults_builder(){
        WebSearchRequest webSearchRequest = WebSearchRequest.builder().searchTerms("query").maxResults(10).build();

        assertThat(webSearchRequest.searchTerms()).isEqualTo("query");
        assertThat(webSearchRequest.startPage()).isEqualTo(1);
        assertThat(webSearchRequest.maxResults()).isEqualTo(10);
        assertThat(webSearchRequest.language()).isNull();
        assertThat(webSearchRequest.geoLocation()).isNull();
        assertThat(webSearchRequest.startIndex()).isNull();
        assertThat(webSearchRequest.safeSearch()).isTrue();
        assertThat(webSearchRequest.additionalParams()).isEmpty();

        assertThat(webSearchRequest).hasToString("WebSearchRequest{searchTerms='query', maxResults=10, language='null', geoLocation='null', startPage=1, startIndex=null, siteRestrict=true, additionalParams={}}");
    }

    @Test
    void test_equals_and_hash(){
        WebSearchRequest wsr1 = WebSearchRequest.from("query", 10);
        WebSearchRequest wsr2 = WebSearchRequest.from("query", 10);

        assertThat(wsr1)
                .isEqualTo(wsr1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsr2)
                .hasSameHashCodeAs(wsr2);

        assertThat(WebSearchRequest.from("other query", 10))
                .isNotEqualTo(wsr1);

        assertThat(WebSearchRequest.from("query", 20))
                .isNotEqualTo(wsr1);
    }

    @Test
    void should_throw_illegalArgumentException_without_searchTerms(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                WebSearchRequest.builder().build());
        assertThat(exception).hasMessage("searchTerms cannot be null or blank");
    }
}
