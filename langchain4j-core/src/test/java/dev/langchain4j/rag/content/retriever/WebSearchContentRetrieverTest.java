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
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSearchContentRetrieverTest{

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
    void should_retrieve_web_pages_back() {
        // given
        ContentRetriever contentRetriever = WebSearchContentRetriever.from(webSearchEngine);

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsExactly(
                Content.from(TextSegment.from("snippet 1",
                        Metadata.from(Stream.of(
                            new AbstractMap.SimpleEntry<>("title", "title 1"),
                            new AbstractMap.SimpleEntry<>("url", "https://google.com")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 2",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("title", "title 2"),
                                new AbstractMap.SimpleEntry<>("url", "https://docs.langchain4j.dev")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 3",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("title", "title 3"),
                                new AbstractMap.SimpleEntry<>("url", "https://github.com/dewitt/opensearch/blob/master/README.md")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                ))
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);
    }

    @Test
    void should_retrieve_web_pages_back_with_builder(){
        // given
        ContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .build();

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsExactly(
                Content.from(TextSegment.from("snippet 1",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("title", "title 1"),
                                new AbstractMap.SimpleEntry<>("url", "https://google.com")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 2",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("title", "title 2"),
                                new AbstractMap.SimpleEntry<>("url", "https://docs.langchain4j.dev")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 3",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("title", "title 3"),
                                new AbstractMap.SimpleEntry<>("url", "https://github.com/dewitt/opensearch/blob/master/README.md")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                ))
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);
    }
}
