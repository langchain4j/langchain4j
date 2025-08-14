package dev.langchain4j.web.search;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import dev.langchain4j.data.document.Metadata;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebSearchResultsTest {

    @Test
    void should_build_webSearchResults() {
        WebSearchResults webSearchResults = WebSearchResults.from(
                WebSearchInformationResult.from(1L),
                singletonList(WebSearchOrganicResult.from("title", URI.create("https://google.com"))));

        assertThat(webSearchResults.results()).hasSize(1);
        assertThat(webSearchResults.results().get(0).url()).hasToString("https://google.com");
        assertThat(webSearchResults.searchInformation().totalResults()).isEqualTo(1L);

        assertThat(webSearchResults)
                .hasToString(
                        "WebSearchResults{searchMetadata={}, searchInformation=WebSearchInformationResult{totalResults=1, pageNumber=null, metadata={}}, results=[WebSearchOrganicResult{title='title', url=https://google.com, snippet='null', content='null', metadata={}}]}");
    }

    @Test
    void equals_and_hash() {
        WebSearchResults wsr1 = WebSearchResults.from(
                WebSearchInformationResult.from(1L),
                singletonList(WebSearchOrganicResult.from("title", URI.create("https://google.com"))));

        WebSearchResults wsr2 = WebSearchResults.from(
                WebSearchInformationResult.from(1L),
                singletonList(WebSearchOrganicResult.from("title", URI.create("https://google.com"))));

        assertThat(wsr1)
                .isEqualTo(wsr1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsr2)
                .hasSameHashCodeAs(wsr2);

        assertThat(WebSearchResults.from(
                        WebSearchInformationResult.from(1L),
                        singletonList(
                                WebSearchOrganicResult.from("title", URI.create("https://docs.langchain4j.dev")))))
                .isNotEqualTo(wsr1);

        assertThat(WebSearchResults.from(
                        WebSearchInformationResult.from(2L),
                        singletonList(WebSearchOrganicResult.from("title", URI.create("https://google.com")))))
                .isNotEqualTo(wsr1);
    }

    @Test
    void should_return_array_of_textSegments_with_snippet() {
        WebSearchResults webSearchResults = WebSearchResults.from(
                WebSearchInformationResult.from(1L),
                singletonList(WebSearchOrganicResult.from("title", URI.create("https://google.com"), "snippet", null)));

        assertThat(webSearchResults.toTextSegments()).hasSize(1);
        assertThat(webSearchResults.toTextSegments().get(0).text()).isEqualTo("title\nsnippet");
        assertThat(webSearchResults.toTextSegments().get(0).metadata())
                .isEqualTo(Metadata.from("url", "https://google.com"));
    }

    @Test
    void should_return_array_of_documents_with_content() {
        WebSearchResults webSearchResults = WebSearchResults.from(
                WebSearchInformationResult.from(1L),
                singletonList(WebSearchOrganicResult.from("title", URI.create("https://google.com"), null, "content")));

        assertThat(webSearchResults.toDocuments()).hasSize(1);
        assertThat(webSearchResults.toDocuments().get(0).text()).isEqualTo("title\ncontent");
        assertThat(webSearchResults.toDocuments().get(0).metadata())
                .isEqualTo(Metadata.from("url", "https://google.com"));
    }

    @Test
    void should_throw_illegalArgumentException_without_searchInformation() {
        // given
        Map<String, Object> searchMetadata = new HashMap<>();
        searchMetadata.put("key", "value");

        // then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new WebSearchResults(
                        searchMetadata,
                        null,
                        singletonList(WebSearchOrganicResult.from(
                                "title", URI.create("https://google.com"), "snippet", null))));
    }
}
