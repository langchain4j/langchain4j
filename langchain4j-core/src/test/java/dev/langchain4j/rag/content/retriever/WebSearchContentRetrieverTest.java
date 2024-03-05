package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.*;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSearchContentRetrieverTest {

    @Test
    void should_retrieve_complete_pages_back() {

        // given
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                           WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                           WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                           WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        ContentRetriever contentRetriever = new WebSearchContentRetriever(webSearchEngine);

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsExactly(
                Content.from(TextSegment.from("snippet 1",
                        Metadata.from(Stream.of(
                            new AbstractMap.SimpleEntry<>("link", "url 1"),
                            new AbstractMap.SimpleEntry<>("title", "title 1")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 2",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 2"),
                                new AbstractMap.SimpleEntry<>("title", "title 2")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 3",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 3"),
                                new AbstractMap.SimpleEntry<>("title", "title 3")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                ))
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);
    }

    @Test
    void should_retrieve_complete_pages_back_with_builder(){
        // given
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

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
                                new AbstractMap.SimpleEntry<>("link", "url 1"),
                                new AbstractMap.SimpleEntry<>("title", "title 1")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 2",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 2"),
                                new AbstractMap.SimpleEntry<>("title", "title 2")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 3",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 3"),
                                new AbstractMap.SimpleEntry<>("title", "title 3")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                ))
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);
    }

    @Test
    void should_retrieve_with_documentSplitter(){
        // given
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        DocumentSplitter whiteSpaceDocumentSplitter = mock(DocumentSplitter.class);
        when(whiteSpaceDocumentSplitter.splitAll(any())).thenReturn(asList(
                TextSegment.from("snippet"),
                TextSegment.from("1"),
                TextSegment.from("snippet"),
                TextSegment.from("2"),
                TextSegment.from("snippet"),
                TextSegment.from("3")
        ));

        ContentRetriever contentRetriever = new WebSearchContentRetriever(
                webSearchEngine,
                whiteSpaceDocumentSplitter);

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsExactly(
                Content.from("snippet"),
                Content.from("1"),
                Content.from("snippet"),
                Content.from("2"),
                Content.from("snippet"),
                Content.from("3")
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(whiteSpaceDocumentSplitter).splitAll(any());
        verifyNoMoreInteractions(whiteSpaceDocumentSplitter);
    }

    @Test
    void should_retrieve_with_documentSplitter_builder(){
        // given
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        DocumentSplitter whiteSpaceDocumentSplitter = mock(DocumentSplitter.class);
        when(whiteSpaceDocumentSplitter.splitAll(any())).thenReturn(asList(
                TextSegment.from("snippet"),
                TextSegment.from("1"),
                TextSegment.from("snippet"),
                TextSegment.from("2"),
                TextSegment.from("snippet"),
                TextSegment.from("3")
        ));

        ContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .documentSplitter(whiteSpaceDocumentSplitter)
                .build();

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsExactly(
                Content.from("snippet"),
                Content.from("1"),
                Content.from("snippet"),
                Content.from("2"),
                Content.from("snippet"),
                Content.from("3")
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(whiteSpaceDocumentSplitter).splitAll(any());
        verifyNoMoreInteractions(whiteSpaceDocumentSplitter);
    }

    @Test
    void should_retrieve_with_embeddingModel(){
        // given
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(asList(embedding, embedding, embedding)));


        ContentRetriever contentRetriever = new WebSearchContentRetriever(
                webSearchEngine,
                embeddingModel);

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).contains(
                Content.from(TextSegment.from("snippet 1",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 1"),
                                new AbstractMap.SimpleEntry<>("title", "title 1")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 2",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 2"),
                                new AbstractMap.SimpleEntry<>("title", "title 2")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 3",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 3"),
                                new AbstractMap.SimpleEntry<>("title", "title 3")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                ))
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(embeddingModel).embed(query.text());
        verify(embeddingModel).embedAll(anyList());
        verifyNoMoreInteractions(embeddingModel);
    }

    @Test
    void should_retrieve_with_embeddingModel_builder(){
        // given
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(asList(embedding, embedding, embedding)));

        ContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .embeddingModel(embeddingModel)
                .build();

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).contains(
                Content.from(TextSegment.from("snippet 1",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 1"),
                                new AbstractMap.SimpleEntry<>("title", "title 1")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 2",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 2"),
                                new AbstractMap.SimpleEntry<>("title", "title 2")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                )),
                Content.from(TextSegment.from("snippet 3",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 3"),
                                new AbstractMap.SimpleEntry<>("title", "title 3")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                ))
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(embeddingModel).embed(query.text());
        verify(embeddingModel).embedAll(anyList());
        verifyNoMoreInteractions(embeddingModel);
    }

    @Test
    void should_retrieve_with_embeddingModel_and_custom_maxResults(){
        // given
        int maxResults = 1;
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        singletonList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1")
                        ),
                        WebSearchInformationResult.informationResult(1L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(singletonList(embedding)));

        ContentRetriever contentRetriever = new WebSearchContentRetriever(
                webSearchEngine,
                embeddingModel,
                maxResults);

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).contains(
                Content.from(TextSegment.from("snippet 1",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 1"),
                                new AbstractMap.SimpleEntry<>("title", "title 1")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                ))
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(embeddingModel).embed(query.text());
        verify(embeddingModel).embedAll(anyList());
        verifyNoMoreInteractions(embeddingModel);
    }

    @Test
    void should_retrieve_with_embeddingModel_and_custom_maxResults_builder(){
        // given
        int maxResults = 1;
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        singletonList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1")
                        ),
                        WebSearchInformationResult.informationResult(1L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(singletonList(embedding)));

        ContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .build();

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).contains(
                Content.from(TextSegment.from("snippet 1",
                        Metadata.from(Stream.of(
                                new AbstractMap.SimpleEntry<>("link", "url 1"),
                                new AbstractMap.SimpleEntry<>("title", "title 1")
                        ).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                ))
        );

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(embeddingModel).embed(query.text());
        verify(embeddingModel).embedAll(anyList());
        verifyNoMoreInteractions(embeddingModel);
    }

    @Test
    void should_retrieve_with_embeddingModel_and_documentSplitter(){
        // given
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(asList(embedding, embedding, embedding, embedding, embedding, embedding)));

        DocumentSplitter whiteSpaceDocumentSplitter = mock(DocumentSplitter.class);
        when(whiteSpaceDocumentSplitter.splitAll(anyList())).thenReturn(asList(
                TextSegment.from("snippet"),
                TextSegment.from("1"),
                TextSegment.from("snippet"),
                TextSegment.from("2"),
                TextSegment.from("snippet"),
                TextSegment.from("3")
        ));

        ContentRetriever contentRetriever = new WebSearchContentRetriever(
                webSearchEngine,
                embeddingModel,
                whiteSpaceDocumentSplitter);

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsAnyElementsOf(
            asList(
                    Content.from("snippet"),
                    Content.from("1"),
                    Content.from("2"),
                    Content.from("3")
            )
        );

        assertThat(contents).hasSize(3); // Default maxResults is 3

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(embeddingModel).embed(query.text());
        verify(embeddingModel).embedAll(anyList());
        verifyNoMoreInteractions(embeddingModel);

        verify(whiteSpaceDocumentSplitter).splitAll(anyList());
        verifyNoMoreInteractions(whiteSpaceDocumentSplitter);
    }

    @Test
    void should_retrieve_with_embeddingModel_and_documentSplitter_builder(){
        // given
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(asList(embedding, embedding, embedding, embedding, embedding, embedding)));

        DocumentSplitter whiteSpaceDocumentSplitter = mock(DocumentSplitter.class);
        when(whiteSpaceDocumentSplitter.splitAll(anyList())).thenReturn(asList(
                TextSegment.from("snippet"),
                TextSegment.from("1"),
                TextSegment.from("snippet"),
                TextSegment.from("2"),
                TextSegment.from("snippet"),
                TextSegment.from("3")
        ));

        ContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .embeddingModel(embeddingModel)
                .documentSplitter(whiteSpaceDocumentSplitter)
                .build();

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsAnyElementsOf(
                asList(
                        Content.from("snippet"),
                        Content.from("1"),
                        Content.from("2"),
                        Content.from("3")
                )
        );

        assertThat(contents).hasSize(3); // Default maxResults is 3

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(embeddingModel).embed(query.text());
        verify(embeddingModel).embedAll(anyList());
        verifyNoMoreInteractions(embeddingModel);

        verify(whiteSpaceDocumentSplitter).splitAll(anyList());
        verifyNoMoreInteractions(whiteSpaceDocumentSplitter);
    }

    @Test
    void should_retrieve_with_embeddingModel_and_documentSplitter_and_custom_maxResults(){
        // given
        int maxResults = 1;
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(asList(embedding, embedding, embedding, embedding, embedding, embedding)));

        DocumentSplitter whiteSpaceDocumentSplitter = mock(DocumentSplitter.class);
        when(whiteSpaceDocumentSplitter.splitAll(anyList())).thenReturn(asList(
                TextSegment.from("snippet"),
                TextSegment.from("1"),
                TextSegment.from("snippet"),
                TextSegment.from("2"),
                TextSegment.from("snippet"),
                TextSegment.from("3")
        ));

        ContentRetriever contentRetriever = new WebSearchContentRetriever(
                webSearchEngine,
                embeddingModel,
                whiteSpaceDocumentSplitter,
                maxResults);

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsAnyElementsOf(
                asList(
                        Content.from("snippet"),
                        Content.from("1"),
                        Content.from("2"),
                        Content.from("3")
                )
        );

        assertThat(contents).hasSize(maxResults);

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(embeddingModel).embed(query.text());
        verify(embeddingModel).embedAll(anyList());
        verifyNoMoreInteractions(embeddingModel);

        verify(whiteSpaceDocumentSplitter).splitAll(anyList());
        verifyNoMoreInteractions(whiteSpaceDocumentSplitter);
    }

    @Test
    void should_retrieve_with_embeddingModel_and_documentSplitter_and_custom_maxResults_builder(){
        // given
        int maxResults = 1;
        WebSearchEngine webSearchEngine = mock(WebSearchEngine.class);
        when(webSearchEngine.search(anyString())).thenReturn(
                new WebSearchResults(
                        asList(
                                WebSearchOrganicResult.from("title 1", "url 1", "snippet 1"),
                                WebSearchOrganicResult.from("title 2", "url 2", "snippet 2"),
                                WebSearchOrganicResult.from("title 3", "url 3", "snippet 3")
                        ),
                        WebSearchInformationResult.informationResult(3L,1, new HashMap<>()),
                        WebSearchPagination.pagination(1)
                )
        );

        Embedding embedding = Embedding.from(asList(1f, 2f, 3f));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(embedding));
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(asList(embedding, embedding, embedding, embedding, embedding, embedding)));

        DocumentSplitter whiteSpaceDocumentSplitter = mock(DocumentSplitter.class);
        when(whiteSpaceDocumentSplitter.splitAll(anyList())).thenReturn(asList(
                TextSegment.from("snippet"),
                TextSegment.from("1"),
                TextSegment.from("snippet"),
                TextSegment.from("2"),
                TextSegment.from("snippet"),
                TextSegment.from("3")
        ));

        ContentRetriever contentRetriever = WebSearchContentRetriever.builder()
                .webSearchEngine(webSearchEngine)
                .embeddingModel(embeddingModel)
                .documentSplitter(whiteSpaceDocumentSplitter)
                .maxResults(maxResults)
                .build();

        Query query = Query.from("query");

        // when
        List<Content> contents = contentRetriever.retrieve(query);

        // then
        assertThat(contents).containsAnyElementsOf(
                asList(
                        Content.from("snippet"),
                        Content.from("1"),
                        Content.from("2"),
                        Content.from("3")
                )
        );

        assertThat(contents).hasSize(maxResults);

        verify(webSearchEngine).search(query.text());
        verifyNoMoreInteractions(webSearchEngine);

        verify(embeddingModel).embed(query.text());
        verify(embeddingModel).embedAll(anyList());
        verifyNoMoreInteractions(embeddingModel);

        verify(whiteSpaceDocumentSplitter).splitAll(anyList());
        verifyNoMoreInteractions(whiteSpaceDocumentSplitter);
    }
}
