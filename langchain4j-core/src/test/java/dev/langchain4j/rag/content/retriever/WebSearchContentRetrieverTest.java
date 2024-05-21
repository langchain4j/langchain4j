package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebSearchContentRetrieverTest {

    WebSearchEngine webSearchEngine;

    @BeforeEach
    void mockWebSearchEngine() {
        webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(any(WebSearchRequest.class))).thenReturn(
                new WebSearchResults(
                        WebSearchInformationResult.from(3L, 1, new HashMap<>()),
                        asList(
                                WebSearchOrganicResult.from("title 1", URI.create("https://google.com"), "snippet 1", null),
                                WebSearchOrganicResult.from("title 2", URI.create("https://docs.langchain4j.dev"), null, "content 2"),
                                WebSearchOrganicResult.from("title 3", URI.create("https://github.com/dewitt/opensearch/blob/master/README.md"), "snippet 3", "content 3")
                        )
                )
        );
    }

    @AfterEach
    void resetWebSearchEngine() {
        reset(webSearchEngine);
    }

    @Test
    void should_retrieve_web_pages_back() {

        // given
        ContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .build();

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsExactly(
                Content.from(TextSegment.from("title 1\nsnippet 1", Metadata.from("url", "https://google.com"))),
                Content.from(TextSegment.from("title 2\ncontent 2", Metadata.from("url", "https://docs.langchain4j.dev"))),
                Content.from(TextSegment.from("title 3\ncontent 3", Metadata.from("url", "https://github.com/dewitt/opensearch/blob/master/README.md")))
        );

        verify(webSearchEngine).search(WebSearchRequest.builder().searchTerms(query.text()).maxResults(3).build());
        verifyNoMoreInteractions(webSearchEngine);
    }
}
