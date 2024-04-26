package dev.langchain4j.rag.retriever;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.rag.retriever.SearchDepth.BASIC;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * A content retriever that retrieves relevant content from the web using Tavily Search API.
 * See more details <a href="https://docs.tavily.com/docs/tavily-api/rest_api">here</a>.
 */
public class TavilyContentRetriever implements ContentRetriever {

    private static final String DEFAULT_BASE_URL = "https://api.tavily.com";
    public static final int DEFAULT_MAX_RESULTS = 3;
    public static final double DEFAULT_MIN_SCORE = 0;
    public static final int DEFAULT_MAX_SEARCH_RESULTS = 5;
    private static final Boolean ALWAYS_INCLUDE_RAW_CONTENT = true;
    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final String apiKey;
    private final TavilyClient tavilyClient;
    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter splitter;
    private final int maxResults;
    private final double minScore;
    private final SearchDepth searchDepth;
    private final Boolean includeImages;
    private final Boolean includeAnswer;
    private final Integer maxSearchResults;
    private final List<String> includeDomains;
    private final List<String> excludeDomains;

    public TavilyContentRetriever(String baseUrl,
                                  String apiKey,
                                  Duration timeout,
                                  EmbeddingModel embeddingModel,
                                  DocumentSplitter splitter,
                                  Integer maxResults,
                                  Double minScore,
                                  SearchDepth searchDepth,
                                  Boolean includeImages,
                                  Boolean includeAnswer,
                                  Integer maxSearchResults,
                                  List<String> includeDomains,
                                  List<String> excludeDomains) {
        this.tavilyClient = TavilyClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();
        this.embeddingModel = embeddingModel;
        this.splitter = splitter;
        this.maxResults = ensureGreaterThanZero(getOrDefault(maxResults, DEFAULT_MAX_RESULTS), "maxResults");
        this.minScore = ensureBetween(getOrDefault(minScore, DEFAULT_MIN_SCORE), 0, 1, "minScore");
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.searchDepth = getOrDefault(searchDepth, BASIC);
        this.includeImages = getOrDefault(includeImages, false);
        this.includeAnswer = getOrDefault(includeAnswer, false);
        this.maxSearchResults = getOrDefault(maxSearchResults, DEFAULT_MAX_SEARCH_RESULTS);
        this.includeDomains = includeDomains;
        this.excludeDomains = excludeDomains;
    }

    @Override
    public List<Content> retrieve(Query query) {

        TavilyResponse tavilyResponse = tavilyClient.search(TavilySearchRequest.builder()
                .apiKey(apiKey)
                .query(query.text())
                .searchDepth(searchDepth)
                .includeImages(includeImages)
                .includeAnswer(includeAnswer)
                .includeRawContent(ALWAYS_INCLUDE_RAW_CONTENT)
                .maxResults(maxSearchResults)
                .includeDomains(includeDomains)
                .excludeDomains(excludeDomains)
                .build());

        if (splitter == null && embeddingModel == null) {
            return tavilyResponse.getResults().stream()
                    .map(result -> Content.from(TextSegment.from(result.getRawContent(), Metadata.from("link", result.getUrl()))))
                    .collect(toList());
        }
        List<Document> documents = tavilyResponse.getResults().stream()
                .map(result -> Document.document(result.getRawContent(), Metadata.from("link", result.getUrl())))
                .collect(toList());

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(documents);

        Response<Embedding> questionEmbedding = embeddingModel.embed(query.text());

        List<EmbeddingMatch<TextSegment>> relevantEmbeddings =
                embeddingStore.findRelevant(questionEmbedding.content(), maxResults, minScore);

        return relevantEmbeddings.stream()
                .map(EmbeddingMatch::embedded)
                .map(Content::new)
                .collect(toList());
    }

    public static TavilyContentRetriever withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        // Required parameters
        private String apiKey;

        // Optional parameters - initialized to default values
        private String baseUrl = "https://api.tavily.com";
        private Duration timeout = Duration.ofSeconds(60);
        private EmbeddingModel embeddingModel = null;
        private DocumentSplitter splitter = null;
        private Integer maxResults = DEFAULT_MAX_RESULTS;
        private Double minScore = DEFAULT_MIN_SCORE;
        private SearchDepth searchDepth = BASIC;
        private Boolean includeImages = false;
        private Boolean includeAnswer = false;
        private Integer maxSearchResults = 5;
        private List<String> includeDomains = new ArrayList<>();
        private List<String> excludeDomains = new ArrayList<>();

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("apiKey must not be null or empty");
            }
            this.apiKey = apiKey;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder embeddingModelAndSlitter(EmbeddingModel embeddingModel, DocumentSplitter splitter) {
            if (embeddingModel == null || splitter == null) {
                throw new IllegalStateException("Both embeddingModel and splitter must be set or not set together.");
            }
            this.embeddingModel = embeddingModel;
            this.splitter = splitter;
            return this;
        }

        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder searchDepth(SearchDepth searchDepth) {
            this.searchDepth = searchDepth;
            return this;
        }

        public Builder includeImages(Boolean includeImages) {
            this.includeImages = includeImages;
            return this;
        }

        public Builder includeAnswer(Boolean includeAnswer) {
            this.includeAnswer = includeAnswer;
            return this;
        }

        public Builder maxSearchResults(Integer maxSearchResults) {
            this.maxSearchResults = maxSearchResults;
            return this;
        }

        public Builder includeDomains(List<String> includeDomains) {
            this.includeDomains = includeDomains;
            return this;
        }

        public Builder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        public TavilyContentRetriever build() {
            return new TavilyContentRetriever(baseUrl, apiKey, timeout, embeddingModel, splitter, maxResults,
                    minScore, searchDepth, includeImages, includeAnswer, maxSearchResults, includeDomains, excludeDomains);
        }
    }

}

