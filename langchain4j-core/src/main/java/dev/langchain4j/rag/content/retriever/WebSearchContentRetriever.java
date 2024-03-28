package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.stream.Collectors.toList;

/**
 * A {@link ContentRetriever} that retrieves relevant {@link Content} from the web using a {@link WebSearchEngine}.
 * <br>
 * By default, this retriever returns complete web pages: one {@link Content} for each web page that a {@link WebSearchEngine} has returned for a given {@link Query}. For some use cases, this might be suboptimal, as complete web pages can contain too much irrelevant content. For example, for the query "Who painted the Mona Lisa?", Google returns a Wikipedia page that contains everything about the Mona Lisa. For such use cases, this retriever can be configured with a {@link DocumentSplitter} and an {@link EmbeddingModel}, which will be used to split web pages into smaller segments and find the most relevant ones, returning only those.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <ul>
 *     <li>{@link DocumentSplitter} - To split the web pages into smaller text segments.</li>
 *     <li>{@link EmbeddingModel} - To embed segments and find the most
 *     relevant ones to the given {@link Query}</li>
 *     <li>{@link #maxTextSegments} - The maximum number of text segments to return.
 *     <br>
 *     Default value is 3.
 *     </li>
 * </ul>
 */
public class WebSearchContentRetriever implements ContentRetriever{

    private static final int DEFAULT_MAX_TEXT_SEGMENTS = 3;
    
    private final WebSearchEngine webSearchEngine;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;
    private final Integer maxTextSegments;


    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine.
     * @param webSearchEngine The web search engine to use for retrieving web search results.
     */
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine) {
        this(webSearchEngine, null);
    }

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine and document splitter.
     * @param webSearchEngine The web search engine to use for retrieving web search results.
     * @param documentSplitter The document splitter to use for splitting web search results into smaller text segments.
     */
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine, DocumentSplitter documentSplitter) {
        this(webSearchEngine, documentSplitter, null, null);
    }

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine, embedding model, document splitter, and maximum results.
     * @param webSearchEngine The web search engine to use for retrieving search results.
     * @param embeddingModel The embedding model to use for generating embeddings and finding the relevant web search results.
     * @param documentSplitter The document splitter to use for splitting search results into text segments.
     * @param maxTextSegments The maximum number of text segments to return.
     */
    @Builder
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine,
                                     DocumentSplitter documentSplitter,
                                     EmbeddingModel embeddingModel,
                                     Integer maxTextSegments) {
        this.webSearchEngine = ensureNotNull(webSearchEngine, "webSearchEngine");
        this.embeddingModel = embeddingModel;
        this.documentSplitter = documentSplitter;
        this.maxTextSegments = ensureGreaterThanZero(getOrDefault(maxTextSegments, DEFAULT_MAX_TEXT_SEGMENTS), "maxResults");
    }

    @Override
    public List<Content> retrieve(Query query) {
        WebSearchResults webSearchResults = webSearchEngine.search(query.text());
        List<TextSegment> textSegments = webSearchResults.toTextSegments();
        if (documentSplitter != null) {
            textSegments = documentSplitter.splitAll(webSearchResults.toDocuments());
        }
        if (embeddingModel != null) {
            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

            Embedding embeddedQuery = embeddingModel.embed(query.text()).content();

            List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();
            embeddingStore.addAll(embeddings, textSegments);

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddedQuery)
                    .maxResults(maxTextSegments)
                    .build();

            List<EmbeddingMatch<TextSegment>> searchResults = embeddingStore.search(searchRequest).matches();
            textSegments = searchResults.stream()
                    .map(EmbeddingMatch::embedded)
                    .collect(toList());
        }
        return textSegments.stream()
                .map(Content::from)
                .collect(toList());
    }
}
