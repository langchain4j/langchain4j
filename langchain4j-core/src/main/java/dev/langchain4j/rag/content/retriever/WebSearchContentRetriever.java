package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchResults;

import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toList;

/**
 * A {@link ContentRetriever} that retrieves relevant {@link Content} from the web using a {@link WebSearchEngine}.
 * <br>
 * It returns one {@link Content} for each result that a {@link WebSearchEngine} has returned for a given {@link Query}.
 * <br>
 * Depending on the {@link WebSearchEngine} implementation, the {@link Content#textSegment()}
 * can contain either a snippet of a web page or a complete content of a web page.
 */
public class WebSearchContentRetriever implements ContentRetriever {

    private final WebSearchEngine webSearchEngine;

    /**
     * Constructs a new WebSearchContentRetriever with the specified web search engine.
     *
     * @param webSearchEngine The web search engine to use for retrieving search results.
     */
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine) {
        this.webSearchEngine = ensureNotNull(webSearchEngine, "webSearchEngine");
    }

    @Override
    public List<Content> retrieve(Query query) {
        WebSearchResults webSearchResults = webSearchEngine.search(query.text());
        return webSearchResults.toTextSegments().stream()
                .map(Content::from)
                .collect(toList());
    }

    /**
     * Creates a new instance of {@code WebSearchContentRetriever} with the specified {@link WebSearchEngine}.
     *
     * @return A new instance of WebSearchContentRetriever.
     */
    public static WebSearchContentRetriever from(WebSearchEngine webSearchEngine) {
        return new WebSearchContentRetriever(webSearchEngine);
    }
}
