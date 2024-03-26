package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
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
 *     relevant web search results based on the {@link Query}</li>
 *     <li>{@link #maxResults} - The maximum number of results to retrieve by the internal {@link EmbeddingStore}.
 *     <br>
 *     Default value is 3.
 *     </li>
 * </ul>
 */
public class WebSearchContentRetriever implements ContentRetriever{

    private static final int DEFAULT_MAX_RESULTS = 3;
    
    private final WebSearchEngine webSearchEngine;
    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int maxResults;

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine.
     * @param webSearchEngine The web search engine to use for retrieving web search results.
     */
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine) {
        this(webSearchEngine, null, null,null);
    }

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine and document splitter.
     * @param webSearchEngine The web search engine to use for retrieving web search results.
     * @param documentSplitter The document splitter to use for splitting web search results into smaller text segments.
     */
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine, DocumentSplitter documentSplitter) {
        this(webSearchEngine, null, documentSplitter,null);
    }

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine and embedding model.
     * @param webSearchEngine The web search engine to use for retrieving web search results.
     * @param embeddingModel The embedding model to use for generating embeddings and finding the most 3 relevant web search results.
     */
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine, EmbeddingModel embeddingModel) {
        this(webSearchEngine, embeddingModel, null,null);
    }

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine, embedding model, and maximum results.
     * @param webSearchEngine The web search engine to use for retrieving search results.
     * @param embeddingModel The embedding model to use for generating embeddings.
     * @param maxResults The maximum number of relevant results to retrieve.
     */
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine, EmbeddingModel embeddingModel, Integer maxResults) {
        this(webSearchEngine, embeddingModel, null, maxResults);
    }

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine, embedding model, and document splitter.
     * @param webSearchEngine The web search engine to use for retrieving search results.
     * @param embeddingModel The embedding model to use for generating embeddings and finding the most relevant 3 web search results.
     * @param documentSplitter The document splitter to use for splitting search results into text segments.
     */
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine,
                                     EmbeddingModel embeddingModel,
                                     DocumentSplitter documentSplitter) {
        this(webSearchEngine, embeddingModel, documentSplitter, DEFAULT_MAX_RESULTS);
    }

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine, embedding model, document splitter, and maximum results.
     * @param webSearchEngine The web search engine to use for retrieving search results.
     * @param embeddingModel The embedding model to use for generating embeddings and finding the relevant web search results.
     * @param documentSplitter The document splitter to use for splitting search results into text segments.
     * @param maxResults The maximum number of relevant results to retrieve.
     */
    @Builder
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine,
                                     EmbeddingModel embeddingModel,
                                     DocumentSplitter documentSplitter,
                                     Integer maxResults) {
        this.webSearchEngine = ensureNotNull(webSearchEngine, "webSearchEngine");
        this.embeddingModel = embeddingModel;
        this.documentSplitter = documentSplitter;
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.maxResults = ensureGreaterThanZero(getOrDefault(maxResults, DEFAULT_MAX_RESULTS), "maxResults");
    }

    @Override
    public List<Content> retrieve(Query query) {
        WebSearchResults webSearchResults = webSearchEngine.search(query.text());
        List<TextSegment> textSegments = webSearchResults.toTextSegments();
        if (documentSplitter != null) {
            textSegments = documentSplitter.splitAll(webSearchResults.toDocuments());
        }
        if (embeddingModel != null) {
            Embedding embedding = embeddingModel.embed(query.text()).content();
            embeddingStore.add(embedding, TextSegment.from(query.text()));

            List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();
            embeddingStore.addAll(embeddings, textSegments);

            List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, maxResults);
            textSegments = relevant.stream()
                    .map(EmbeddingMatch::embedded)
                    .collect(toList());
        }
        return textSegments.stream()
                .map(Content::from)
                .collect(toList());
    }
}
