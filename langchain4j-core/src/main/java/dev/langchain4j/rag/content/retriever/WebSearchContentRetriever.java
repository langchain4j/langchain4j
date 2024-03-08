package dev.langchain4j.rag.content.retriever;

import dev.langchain4j.WillChangeSoon;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.web.WebResult;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.tool.web.search.WebSearchTool;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.stream.Collectors.toList;

@WillChangeSoon("Retrieve() logic may change in the future after feedback")
public class WebSearchContentRetriever implements ContentRetriever{

    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final double DEFAULT_MIN_SCORE = 0;

    private final WebSearchTool webSearchTool;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int maxResults;
    private final double minScore;

    public WebSearchContentRetriever(WebSearchTool webSearchTool,
                                     EmbeddingModel embeddingModel,
                                     EmbeddingStore<TextSegment> embeddingStore) {
        this(webSearchTool, embeddingModel, embeddingStore, DEFAULT_MAX_RESULTS, DEFAULT_MIN_SCORE);
    }

    public WebSearchContentRetriever(WebSearchTool webSearchTool,
                                     EmbeddingModel embeddingModel,
                                     EmbeddingStore<TextSegment> embeddingStore,
                                     int maxResults) {
        this(webSearchTool, embeddingModel, embeddingStore, maxResults, DEFAULT_MIN_SCORE);
    }

    @Builder
    public WebSearchContentRetriever(WebSearchTool webSearchTool,
                                     EmbeddingModel embeddingModel,
                                     EmbeddingStore<TextSegment> embeddingStore,
                                     Integer maxResults,
                                     Double minScore) {
        this.webSearchTool = ensureNotNull(webSearchTool, "webSearchTool");
        this.embeddingModel = ensureNotNull(embeddingModel, "embeddingModel");
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.maxResults = ensureGreaterThanZero(getOrDefault(maxResults, DEFAULT_MAX_RESULTS), "maxResults");
        this.minScore = ensureBetween(getOrDefault(minScore, DEFAULT_MIN_SCORE), 0, 1, "minScore");
    }


    @Override
    public List<Content> retrieve(Query query) {
        List<WebResult> webResults = webSearchTool.searchResults(query.text());
        // I am not sure if the extraction of relevant content using the embeddingModel and embeddingStore should be applied here
        // OR this should be scored by a scoringModel as a post process once all the results are retrieved from the web (aggregator).
        // Your feedback is required here
        // PS: Most python projects return the List<Content> as it was retrieved from the web search tool
        Response<List<Embedding>> embeddedSnippets = embeddingModel.embedAll(webResults.stream()
                .map(WebResult::toTextSegment)
                .collect(toList()));

        List<EmbeddingMatch<TextSegment>> relevantSnippets = embeddedSnippets.content().stream()
                .flatMap(embedding -> embeddingStore.findRelevant(embedding, maxResults, minScore).stream())
                .collect(toList());

        return relevantSnippets.stream()
                .map(EmbeddingMatch::embedded)
                .map(Content::from)
                .collect(toList());
    }
}
