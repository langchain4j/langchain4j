package dev.langchain4j.web.search.tavily;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.internal.UriUtils;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;

/**
 * Represents Tavily Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://docs.tavily.com/documentation/api-reference/endpoint/search">here</a>.
 * <br>
 * When {@link #includeRawContent} is set to {@code true},
 * the raw content will appear in the {@link WebSearchOrganicResult#content()} field of each result.
 * <br>
 * When {@link #includeAnswer} is set to {@code true},
 * the answer will appear in the {@link WebSearchOrganicResult#snippet()} field of the first result.
 * In this case, the {@link WebSearchOrganicResult#url()} of the first result will always be "https://tavily.com/" and
 * the {@link WebSearchOrganicResult#title()} will always be "Tavily Search API".
 * <br>
 * Additional parameters:
 * <ul>
 *   <li>{@link #topic} - Category of the search: "general" (default), "news", or "finance"</li>
 *   <li>{@link #timeRange} - Time range filter: "day", "week", "month", or "year"</li>
 *   <li>{@link #startDate} - Start date for results in "YYYY-MM-DD" format</li>
 *   <li>{@link #endDate} - End date for results in "YYYY-MM-DD" format</li>
 *   <li>{@link #includeImages} - Include query-related images in the response</li>
 *   <li>{@link #includeImageDescriptions} - Include descriptions for images (requires includeImages)</li>
 *   <li>{@link #includeFavicon} - Include a favicon URL for each result</li>
 *   <li>{@link #country} - Country to boost search results from (e.g. "france", "united states")</li>
 *   <li>{@link #chunksPerSource} - Number of content chunks per source (1-3, only for advanced depth)</li>
 *   <li>{@link #autoParameters} - Let Tavily auto-select optimal parameters</li>
 *   <li>{@link #exactMatch} - Require exact phrase matching for the query</li>
 * </ul>
 */
public class TavilyWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://api.tavily.com/";

    private final String apiKey;
    private final TavilyClient tavilyClient;
    private final String searchDepth;
    private final Boolean includeAnswer;
    private final Boolean includeRawContent;
    private final List<String> includeDomains;
    private final List<String> excludeDomains;
    private final String topic;
    private final String timeRange;
    private final String startDate;
    private final String endDate;
    private final Boolean includeImages;
    private final Boolean includeImageDescriptions;
    private final Boolean includeFavicon;
    private final String country;
    private final Integer chunksPerSource;
    private final Boolean autoParameters;
    private final Boolean exactMatch;

    public TavilyWebSearchEngine(
            String baseUrl,
            String apiKey,
            Duration timeout,
            String searchDepth,
            Boolean includeAnswer,
            Boolean includeRawContent,
            List<String> includeDomains,
            List<String> excludeDomains,
            String topic,
            String timeRange,
            String startDate,
            String endDate,
            Boolean includeImages,
            Boolean includeImageDescriptions,
            Boolean includeFavicon,
            String country,
            Integer chunksPerSource,
            Boolean autoParameters,
            Boolean exactMatch) {
        this.tavilyClient = TavilyClient.builder()
                .baseUrl(getOrDefault(baseUrl, DEFAULT_BASE_URL))
                .timeout(getOrDefault(timeout, ofSeconds(10)))
                .build();
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.searchDepth = searchDepth;
        this.includeAnswer = includeAnswer;
        this.includeRawContent = includeRawContent;
        this.includeDomains = copyIfNotNull(includeDomains);
        this.excludeDomains = copyIfNotNull(excludeDomains);
        this.topic = topic;
        this.timeRange = timeRange;
        this.startDate = startDate;
        this.endDate = endDate;
        this.includeImages = includeImages;
        this.includeImageDescriptions = includeImageDescriptions;
        this.includeFavicon = includeFavicon;
        this.country = country;
        this.chunksPerSource = chunksPerSource;
        this.autoParameters = autoParameters;
        this.exactMatch = exactMatch;
    }

    public static TavilyWebSearchEngineBuilder builder() {
        return new TavilyWebSearchEngineBuilder();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {

        TavilySearchRequest request = TavilySearchRequest.builder()
                .apiKey(apiKey)
                .query(webSearchRequest.searchTerms())
                .searchDepth(searchDepth)
                .includeAnswer(includeAnswer)
                .includeRawContent(includeRawContent)
                .maxResults(webSearchRequest.maxResults())
                .includeDomains(includeDomains)
                .excludeDomains(excludeDomains)
                .topic(topic)
                .timeRange(timeRange)
                .startDate(startDate)
                .endDate(endDate)
                .includeImages(includeImages)
                .includeImageDescriptions(includeImageDescriptions)
                .includeFavicon(includeFavicon)
                .country(country)
                .chunksPerSource(chunksPerSource)
                .autoParameters(autoParameters)
                .exactMatch(exactMatch)
                .build();

        TavilyResponse tavilyResponse = tavilyClient.search(request);

        final List<WebSearchOrganicResult> results = tavilyResponse.getResults().stream()
                .map(TavilyWebSearchEngine::toWebSearchOrganicResult)
                .collect(toList());

        if (tavilyResponse.getAnswer() != null) {
            WebSearchOrganicResult answerResult = WebSearchOrganicResult.from(
                    "Tavily Search API", URI.create("https://tavily.com/"), tavilyResponse.getAnswer(), null);
            results.add(0, answerResult);
        }

        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
    }

    public static TavilyWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(TavilySearchResult tavilySearchResult) {
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("score", String.valueOf(tavilySearchResult.getScore()));
        if (tavilySearchResult.getPublishedDate() != null) {
            metadata.put("publishedDate", tavilySearchResult.getPublishedDate());
        }
        if (tavilySearchResult.getFavicon() != null) {
            metadata.put("favicon", tavilySearchResult.getFavicon());
        }
        return WebSearchOrganicResult.from(
                tavilySearchResult.getTitle(),
                UriUtils.createUriSafely(tavilySearchResult.getUrl()),
                tavilySearchResult.getContent(),
                tavilySearchResult.getRawContent(),
                metadata);
    }

    public static class TavilyWebSearchEngineBuilder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private String searchDepth;
        private Boolean includeAnswer;
        private Boolean includeRawContent;
        private List<String> includeDomains;
        private List<String> excludeDomains;
        private String topic;
        private String timeRange;
        private String startDate;
        private String endDate;
        private Boolean includeImages;
        private Boolean includeImageDescriptions;
        private Boolean includeFavicon;
        private String country;
        private Integer chunksPerSource;
        private Boolean autoParameters;
        private Boolean exactMatch;

        TavilyWebSearchEngineBuilder() {}

        public TavilyWebSearchEngineBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public TavilyWebSearchEngineBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public TavilyWebSearchEngineBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public TavilyWebSearchEngineBuilder searchDepth(String searchDepth) {
            this.searchDepth = searchDepth;
            return this;
        }

        public TavilyWebSearchEngineBuilder includeAnswer(Boolean includeAnswer) {
            this.includeAnswer = includeAnswer;
            return this;
        }

        public TavilyWebSearchEngineBuilder includeRawContent(Boolean includeRawContent) {
            this.includeRawContent = includeRawContent;
            return this;
        }

        public TavilyWebSearchEngineBuilder includeDomains(List<String> includeDomains) {
            this.includeDomains = includeDomains;
            return this;
        }

        public TavilyWebSearchEngineBuilder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        public TavilyWebSearchEngineBuilder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public TavilyWebSearchEngineBuilder timeRange(String timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public TavilyWebSearchEngineBuilder startDate(String startDate) {
            this.startDate = startDate;
            return this;
        }

        public TavilyWebSearchEngineBuilder endDate(String endDate) {
            this.endDate = endDate;
            return this;
        }

        public TavilyWebSearchEngineBuilder includeImages(Boolean includeImages) {
            this.includeImages = includeImages;
            return this;
        }

        public TavilyWebSearchEngineBuilder includeImageDescriptions(Boolean includeImageDescriptions) {
            this.includeImageDescriptions = includeImageDescriptions;
            return this;
        }

        public TavilyWebSearchEngineBuilder includeFavicon(Boolean includeFavicon) {
            this.includeFavicon = includeFavicon;
            return this;
        }

        public TavilyWebSearchEngineBuilder country(String country) {
            this.country = country;
            return this;
        }

        public TavilyWebSearchEngineBuilder chunksPerSource(Integer chunksPerSource) {
            this.chunksPerSource = chunksPerSource;
            return this;
        }

        public TavilyWebSearchEngineBuilder autoParameters(Boolean autoParameters) {
            this.autoParameters = autoParameters;
            return this;
        }

        public TavilyWebSearchEngineBuilder exactMatch(Boolean exactMatch) {
            this.exactMatch = exactMatch;
            return this;
        }

        public TavilyWebSearchEngine build() {
            return new TavilyWebSearchEngine(
                    this.baseUrl,
                    this.apiKey,
                    this.timeout,
                    this.searchDepth,
                    this.includeAnswer,
                    this.includeRawContent,
                    this.includeDomains,
                    this.excludeDomains,
                    this.topic,
                    this.timeRange,
                    this.startDate,
                    this.endDate,
                    this.includeImages,
                    this.includeImageDescriptions,
                    this.includeFavicon,
                    this.country,
                    this.chunksPerSource,
                    this.autoParameters,
                    this.exactMatch);
        }

        public String toString() {
            return "TavilyWebSearchEngine.TavilyWebSearchEngineBuilder(baseUrl=" + this.baseUrl + ", apiKey="
                    + this.apiKey + ", timeout=" + this.timeout + ", searchDepth=" + this.searchDepth
                    + ", includeAnswer=" + this.includeAnswer + ", includeRawContent=" + this.includeRawContent
                    + ", includeDomains=" + this.includeDomains + ", excludeDomains=" + this.excludeDomains
                    + ", topic=" + this.topic + ", timeRange=" + this.timeRange + ", startDate=" + this.startDate
                    + ", endDate=" + this.endDate + ", includeImages=" + this.includeImages
                    + ", includeImageDescriptions=" + this.includeImageDescriptions + ", includeFavicon="
                    + this.includeFavicon + ", country=" + this.country + ", chunksPerSource=" + this.chunksPerSource
                    + ", autoParameters=" + this.autoParameters + ", exactMatch=" + this.exactMatch + ")";
        }
    }
}
