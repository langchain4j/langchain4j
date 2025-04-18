package dev.langchain4j.web.search.tavily;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

/**
 * Represents Tavily Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://docs.tavily.com/docs/tavily-api/rest_api">here</a>.
 * <br>
 * When {@link #includeRawContent} is set to {@code true},
 * the raw content will appear in the {@link WebSearchOrganicResult#content()} field of each result.
 * <br>
 * When {@link #includeAnswer} is set to {@code true},
 * the answer will appear in the {@link WebSearchOrganicResult#snippet()} field of the first result.
 * In this case, the {@link WebSearchOrganicResult#url()} of the first result will always be "https://tavily.com/" and
 * the {@link WebSearchOrganicResult#title()} will always be "Tavily Search API".
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

    public TavilyWebSearchEngine(String baseUrl,
                                 String apiKey,
                                 Duration timeout,
                                 String searchDepth,
                                 Boolean includeAnswer,
                                 Boolean includeRawContent,
                                 List<String> includeDomains,
                                 List<String> excludeDomains) {
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
                .build();

        TavilyResponse tavilyResponse = tavilyClient.search(request);

        final List<WebSearchOrganicResult> results = tavilyResponse.getResults().stream()
                .map(TavilyWebSearchEngine::toWebSearchOrganicResult)
                .collect(toList());

        if (tavilyResponse.getAnswer() != null) {
            WebSearchOrganicResult answerResult = WebSearchOrganicResult.from(
                    "Tavily Search API",
                    URI.create("https://tavily.com/"),
                    tavilyResponse.getAnswer(),
                    null
            );
            results.add(0, answerResult);
        }

        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
    }

    public static TavilyWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(TavilySearchResult tavilySearchResult) {
        String safeUrlEncoded = URLEncoder.encode(tavilySearchResult.getUrl(), StandardCharsets.UTF_8).replace("+", "%20");
        return WebSearchOrganicResult.from(tavilySearchResult.getTitle(),
                URI.create(safeUrlEncoded),
                tavilySearchResult.getContent(),
                tavilySearchResult.getRawContent(),
                Collections.singletonMap("score", String.valueOf(tavilySearchResult.getScore())));
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

        TavilyWebSearchEngineBuilder() {
        }

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

        public TavilyWebSearchEngine build() {
            return new TavilyWebSearchEngine(this.baseUrl, this.apiKey, this.timeout, this.searchDepth, this.includeAnswer, this.includeRawContent, this.includeDomains, this.excludeDomains);
        }

        public String toString() {
            return "TavilyWebSearchEngine.TavilyWebSearchEngineBuilder(baseUrl=" + this.baseUrl + ", apiKey=" + this.apiKey + ", timeout=" + this.timeout + ", searchDepth=" + this.searchDepth + ", includeAnswer=" + this.includeAnswer + ", includeRawContent=" + this.includeRawContent + ", includeDomains=" + this.includeDomains + ", excludeDomains=" + this.excludeDomains + ")";
        }
    }
}

