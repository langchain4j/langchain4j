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
       WebSearchResults webSearchResults = WebSearchResults.from(
               singletonList(WebSearchOrganicResult.from("content")),
               WebSearchInformationResult.from(1L),
               WebSearchPagination.from(1));

       assertThat(webSearchResults.results()).hasSize(1);
       assertThat(webSearchResults.results().get(0).content()).isEqualTo("content");
       assertThat(webSearchResults.searchInformation().totalResults()).isEqualTo(1L);
       assertThat(webSearchResults.pagination().current()).isEqualTo(1);

       assertThat(webSearchResults).hasToString("WebSearchResults{searchMetadata=null, searchInformation=WebSearchInformationResult{totalResults=1, pageNumber=null, searchInformation=null}, results=[WebSearchOrganicResult{title='null', link='null', content='content', resultMetadata={}}], pagination=WebSearchPagination{current=1, next='null', previous='null', otherPages=null}}");
    }

    @Test
    void test_equals_and_hash(){
        WebSearchResults wsr1 = WebSearchResults.from(
                singletonList(WebSearchOrganicResult.from("content")),
                WebSearchInformationResult.from(1L),
                WebSearchPagination.from(1));

        WebSearchResults wsr2 = WebSearchResults.from(
                singletonList(WebSearchOrganicResult.from("content")),
                WebSearchInformationResult.from(1L),
                WebSearchPagination.from(1));

        assertThat(wsr1)
                .isEqualTo(wsr1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsr2)
                .hasSameHashCodeAs(wsr2);

        assertThat(WebSearchResults.from(
                singletonList(WebSearchOrganicResult.from("other content")),
                WebSearchInformationResult.from(1L),
                WebSearchPagination.from(1)))
                .isNotEqualTo(wsr1);

        assertThat(WebSearchResults.from(
                singletonList(WebSearchOrganicResult.from("content")),
                WebSearchInformationResult.from(2L),
                WebSearchPagination.from(1)))
                .isNotEqualTo(wsr1);

        assertThat(WebSearchResults.from(
                singletonList(WebSearchOrganicResult.from("content")),
                WebSearchInformationResult.from(1L),
                WebSearchPagination.from(2)))
                .isNotEqualTo(wsr1);
    }

    @Test
    void should_return_array_of_textSegments(){
        WebSearchResults webSearchResults = WebSearchResults.from(
                singletonList(WebSearchOrganicResult.from("title","url","content")),
                WebSearchInformationResult.from(1L),
                WebSearchPagination.from(1));

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
        WebSearchResults webSearchResults = WebSearchResults.from(
                singletonList(WebSearchOrganicResult.from("title","url","content")),
                WebSearchInformationResult.from(1L),
                WebSearchPagination.from(1));

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
                    singletonList(WebSearchOrganicResult.from("content")),
                    WebSearchPagination.from(1));
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
                    WebSearchInformationResult.from(1L),
                    anyList(),
                    WebSearchPagination.from(1));
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
                    WebSearchInformationResult.from(1L),
                    singletonList(WebSearchOrganicResult.from("content")),
                    null);
        });
    }

}
