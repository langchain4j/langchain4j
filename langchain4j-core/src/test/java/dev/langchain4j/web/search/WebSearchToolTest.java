package dev.langchain4j.web.search;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class WebSearchToolTest {

    WebSearchEngine webSearchEngine;

    @BeforeEach
    void mockWebSearchEngine(){
        webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        WebSearchInformationResult.from(3L,1, new HashMap<>()),
                        asList(
                                WebSearchOrganicResult.from("title 1", URI.create("https://google.com"), "snippet 1"),
                                WebSearchOrganicResult.from("title 2", URI.create("https://docs.langchain4j.dev"), "snippet 2"),
                                WebSearchOrganicResult.from("title 3", URI.create("https://github.com/dewitt/opensearch/blob/master/README.md"), "snippet 3")
                        )
                )
        );
    }

    @AfterEach
    void resetWebSearchEngine(){
        reset(webSearchEngine);
    }

    @Test
    void should_build_webSearchTool(){
        // given
        String searchTerm = "Any text to search";
        WebSearchTool webSearchTool = WebSearchTool.from(webSearchEngine);

        // when
        String strResult = webSearchTool.runSearch(searchTerm);

        // then
        assertThat(strResult).isNotBlank();
        assertThat(strResult)
                .as("At least one result should be contains 'title 1' and 'https://google.com' and 'snippet 1'")
                .contains("Title: title 1\nURL Source: https://google.com\nSnippet:\nsnippet 1");

        verify(webSearchEngine).search(searchTerm);
        verifyNoMoreInteractions(webSearchEngine);
    }
}
