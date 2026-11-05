package dev.langchain4j.web.search;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebSearchToolTest {

    WebSearchEngine webSearchEngine;

    @BeforeEach
    void mockWebSearchEngine() {
        webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString()))
                .thenReturn(new WebSearchResults(
                        WebSearchInformationResult.from(3L, 1, new HashMap<>()),
                        asList(
                                WebSearchOrganicResult.from(
                                        "title 1", URI.create("https://google.com"), "snippet 1", "content 1"),
                                WebSearchOrganicResult.from(
                                        "title 2",
                                        URI.create("https://docs.langchain4j.dev"),
                                        "snippet 2",
                                        "content 2"),
                                WebSearchOrganicResult.from(
                                        "title 3",
                                        URI.create("https://github.com/dewitt/opensearch/blob/master/README.md"),
                                        "snippet 3",
                                        "content 3"))));
    }

    @AfterEach
    void resetWebSearchEngine() {
        reset(webSearchEngine);
    }

    @Test
    void should_build_webSearchTool() {
        // given
        String searchTerm = "Any text to search";
        WebSearchTool webSearchTool = WebSearchTool.from(webSearchEngine);

        // when
        String strResult = webSearchTool.searchWeb(searchTerm);

        // then
        assertThat(strResult).isNotBlank();
        assertThat(strResult)
                .as("At least one result should be contains 'title 1' and 'https://google.com' and 'content 1'")
                .contains("Title: title 1\nSource: https://google.com\nContent:\ncontent 1");

        verify(webSearchEngine).search(searchTerm);
        verifyNoMoreInteractions(webSearchEngine);
    }

    @Test
    void should_fall_back_to_snippet_and_omit_missing_text() {
        // given
        when(webSearchEngine.search(anyString()))
                .thenReturn(WebSearchResults.from(
                        WebSearchInformationResult.from(3L),
                        asList(
                                WebSearchOrganicResult.from("title 1", URI.create("https://google.com")),
                                WebSearchOrganicResult.from(
                                        "title 2", URI.create("https://docs.langchain4j.dev"), "snippet 2", null),
                                WebSearchOrganicResult.from(
                                        "title 3", URI.create("https://github.com"), "snippet 3", " "))));
        WebSearchTool webSearchTool = WebSearchTool.from(webSearchEngine);

        // when
        String strResult = webSearchTool.searchWeb("Any text to search");

        // then
        assertThat(strResult)
                .isEqualTo("Title: title 1\nSource: https://google.com\n\n"
                        + "Title: title 2\nSource: https://docs.langchain4j.dev\nSnippet:\nsnippet 2\n\n"
                        + "Title: title 3\nSource: https://github.com\nSnippet:\nsnippet 3");
    }
}
