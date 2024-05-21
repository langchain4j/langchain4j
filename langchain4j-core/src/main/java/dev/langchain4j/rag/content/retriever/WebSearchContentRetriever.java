package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
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
    private final int maxResults;

    @Builder
    public WebSearchContentRetriever(WebSearchEngine webSearchEngine, Integer maxResults) {
        this.webSearchEngine = ensureNotNull(webSearchEngine, "webSearchEngine");
        this.maxResults = getOrDefault(maxResults, 5);
    }

    @Override
    public List<Content> retrieve(Query query) {

        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query.text())
                .maxResults(maxResults)
                .build();

        WebSearchResults webSearchResults = webSearchEngine.search(webSearchRequest);

        return webSearchResults.toTextSegments().stream()
                .map(Content::from)
                .collect(toList());
    }
}
