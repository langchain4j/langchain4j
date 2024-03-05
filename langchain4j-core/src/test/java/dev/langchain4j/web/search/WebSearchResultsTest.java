package dev.langchain4j.web.search;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;

class WebSearchResultsTest {

    @Test
    void should_build_webSearchResults(){
       WebSearchResults webSearchResults = WebSearchResults.webSearchResults(
               singletonList(WebSearchOrganicResult.webSearchOrganicResult("content")),
               WebSearchInformationResult.informationResult(1L),
               WebSearchPagination.pagination(1));

       assertThat(webSearchResults.results()).hasSize(1);
       assertThat(webSearchResults.results().get(0).content()).isEqualTo("content");
       assertThat(webSearchResults.searchInformation().totalResults()).isEqualTo(1L);
       assertThat(webSearchResults.pagination().current()).isEqualTo(1);

       assertThat(webSearchResults).hasToString("WebSearchResults{searchMetadata=null, searchInformation=WebSearchInformationResult{totalResults=1, pageNumber=null, searchInformation=null}, results=[WebSearchOrganicResult{title='noTitle', link='noLink', content='content', resultMetadata={}}], pagination=WebSearchPagination{current=1, next='null', previous='null', otherPages=null}}");
    }

    @Test
    void test_equals_and_hash(){
        WebSearchResults wsr1 = WebSearchResults.webSearchResults(
                singletonList(WebSearchOrganicResult.webSearchOrganicResult("content")),
                WebSearchInformationResult.informationResult(1L),
                WebSearchPagination.pagination(1));

        WebSearchResults wsr2 = WebSearchResults.webSearchResults(
                singletonList(WebSearchOrganicResult.webSearchOrganicResult("content")),
                WebSearchInformationResult.informationResult(1L),
                WebSearchPagination.pagination(1));

        assertThat(wsr1)
                .isEqualTo(wsr1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsr2)
                .hasSameHashCodeAs(wsr2);

        assertThat(WebSearchResults.webSearchResults(
                singletonList(WebSearchOrganicResult.webSearchOrganicResult("other content")),
                WebSearchInformationResult.informationResult(1L),
                WebSearchPagination.pagination(1)))
                .isNotEqualTo(wsr1);

        assertThat(WebSearchResults.webSearchResults(
                singletonList(WebSearchOrganicResult.webSearchOrganicResult("content")),
                WebSearchInformationResult.informationResult(2L),
                WebSearchPagination.pagination(1)))
                .isNotEqualTo(wsr1);

        assertThat(WebSearchResults.webSearchResults(
                singletonList(WebSearchOrganicResult.webSearchOrganicResult("content")),
                WebSearchInformationResult.informationResult(1L),
                WebSearchPagination.pagination(2)))
                .isNotEqualTo(wsr1);
    }

    @Test
    void should_return_array_of_textSegments(){
        WebSearchResults webSearchResults = WebSearchResults.webSearchResults(
                singletonList(WebSearchOrganicResult.webSearchOrganicResult("title","url","content")),
                WebSearchInformationResult.informationResult(1L),
                WebSearchPagination.pagination(1));

        assertThat(webSearchResults.toTextSegments()).hasSize(1);
        assertThat(webSearchResults.toTextSegments().get(0).text()).isEqualTo("content");
        assertThat(webSearchResults.toTextSegments().get(0).metadata()).isEqualTo(
                Metadata.from(Stream.of(
                        new AbstractMap.SimpleEntry<>("link", "url"),
                        new AbstractMap.SimpleEntry<>("title", "title")
                ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
        );
    }

    @Test
    void should_return_array_of_documents(){
        WebSearchResults webSearchResults = WebSearchResults.webSearchResults(
                singletonList(WebSearchOrganicResult.webSearchOrganicResult("title","url","content")),
                WebSearchInformationResult.informationResult(1L),
                WebSearchPagination.pagination(1));

        assertThat(webSearchResults.toDocuments()).hasSize(1);
        assertThat(webSearchResults.toDocuments().get(0).text()).isEqualTo("content");
        assertThat(webSearchResults.toDocuments().get(0).metadata()).isEqualTo(
                Metadata.from(Stream.of(
                        new AbstractMap.SimpleEntry<>("link", "url"),
                        new AbstractMap.SimpleEntry<>("title", "title")
                ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
        );
    }

    @Test
    void should_throw_illegalArgumentException_without_searchInformation(){
        // given
        Map<String, Object> searchMetadata = new HashMap<>();
        searchMetadata.put("key", "value");

        // then
        assertThrows(IllegalArgumentException.class, () -> {
            new WebSearchResults(
                    searchMetadata,
                    null,
                    singletonList(WebSearchOrganicResult.webSearchOrganicResult("content")),
                    WebSearchPagination.pagination(1));
        });
    }

    @Test
    void should_throw_illegalArgumentException_without_results(){
        // given
        Map<String, Object> searchMetadata = new HashMap<>();
        searchMetadata.put("key", "value");

        // then
        assertThrows(IllegalArgumentException.class, () -> {
            new WebSearchResults(
                    searchMetadata,
                    WebSearchInformationResult.informationResult(1L),
                    anyList(),
                    WebSearchPagination.pagination(1));
        });
    }

    @Test
    void should_throw_illegalArgumentException_without_pagination(){
        // given
        Map<String, Object> searchMetadata = new HashMap<>();
        searchMetadata.put("key", "value");

        // then
        assertThrows(IllegalArgumentException.class, () -> {
            new WebSearchResults(
                    searchMetadata,
                    WebSearchInformationResult.informationResult(1L),
                    singletonList(WebSearchOrganicResult.webSearchOrganicResult("content")),
                    null);
        });
    }

}
