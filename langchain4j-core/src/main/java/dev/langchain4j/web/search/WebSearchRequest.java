package dev.langchain4j.web.search;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;


/**
 * Represents a search request that can be made by the user to perform searches in any implementation of {@link WebSearchEngine}.
 * <p>
 * {@link WebSearchRequest} follow opensearch foundation standard implemented by most web search engine libs like Google, Bing, Yahoo, etc.
 * <a href="https://github.com/dewitt/opensearch/blob/master/opensearch-1-1-draft-6.md#opensearch-11-parameters">OpenSearch#parameters</a>
 * </p>
 * <p>
 * The {@link #searchTerms} are the keywords that the search client desires to search for. This param is mandatory to perform a search.
 * <p>
 * <br>
 * Configurable parameters (optional):
 * <ul>
 *    <li>{@link #maxResults} - The expected number of results to be found if the search request were made. Each search engine may have a different limit for the maximum number of results that can be returned.</li>
 *    <li>{@link #language} - The desired language for search results is a string that indicates that the search client desires search results in the specified language. Each search engine may have a different set of supported languages.</li>
 *    <li>{@link #geoLocation} - The desired geolocation for search results is a string that indicates that the search client desires search results in the specified geolocation. Each search engine may have a different set of supported geolocations.</li>
 *    <li>{@link #startPage} - The start page number for search results is the page number of the set of search results desired by the search user.</li>
 *    <li>{@link #startIndex} - The start index for search results is the index of the first search result desired by the search user. Each search engine may have a different set of supported start indexes in combination with the start page number.</li>
 *    <li>{@link #safeSearch} - The safe search flag is a boolean that indicates that the search client desires search results with safe search enabled or disabled.</li>
 *    <li>{@link #additionalParams} - The additional parameters for the search request are a map of key-value pairs that represent additional parameters for the search request. It's a way to be flex and add custom param for each search engine.</li>
 * </ul>
 */
public class WebSearchRequest {

    private final String searchTerms;
    private final Integer maxResults;
    private final String language;
    private final String geoLocation;
    private final Integer startPage;
    private final Integer startIndex;
    private final Boolean safeSearch;
    private final Map<String, Object> additionalParams;

    private WebSearchRequest(Builder builder){
        this.searchTerms = ensureNotBlank(builder.searchTerms,"searchTerms");
        this.maxResults = builder.maxResults;
        this.language = builder.language;
        this.geoLocation = builder.geoLocation;
        this.startPage = getOrDefault(builder.startPage,1);
        this.startIndex = builder.startIndex;
        this.safeSearch = getOrDefault(builder.safeSearch,true);
        this.additionalParams = getOrDefault(builder.additionalParams, () -> new HashMap<>());
    }

    /**
     * Get the search terms.
     *
     * @return The search terms.
     */
    public String searchTerms() {
        return searchTerms;
    }

    /**
     * Get the maximum number of results.
     *
     * @return The maximum number of results.
     */
    public Integer maxResults() {
        return maxResults;
    }

    /**
     * Get the desired language for search results.
     *
     * @return The desired language for search results.
     */
    public String language() {
        return language;
    }

    /**
     * Get the desired geolocation for search results.
     *
     * @return The desired geolocation for search results.
     */
    public String geoLocation() {
        return geoLocation;
    }

    /**
     * Get the start page number for search results.
     *
     * @return The start page number for search results.
     */
    public Integer startPage() {
        return startPage;
    }

    /**
     * Get the start index for search results.
     *
     * @return The start index for search results.
     */
    public Integer startIndex() {
        return startIndex;
    }

    /**
     * Get the safe search flag.
     *
     * @return The safe search flag.
     */
    public Boolean safeSearch() {
        return safeSearch;
    }

    /**
     * Get the additional parameters for the search request.
     *
     * @return The additional parameters for the search request.
     */
    public Map<String, Object> additionalParams() {
        return additionalParams;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof WebSearchRequest
                && equalTo((WebSearchRequest) another);
    }

    private boolean equalTo(WebSearchRequest another){
        return Objects.equals(searchTerms, another.searchTerms)
                && Objects.equals(maxResults, another.maxResults)
                && Objects.equals(language, another.language)
                && Objects.equals(geoLocation, another.geoLocation)
                && Objects.equals(startPage, another.startPage)
                && Objects.equals(startIndex, another.startIndex)
                && Objects.equals(safeSearch, another.safeSearch)
                && Objects.equals(additionalParams, another.additionalParams);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(searchTerms);
        h += (h << 5) + Objects.hashCode(maxResults);
        h += (h << 5) + Objects.hashCode(language);
        h += (h << 5) + Objects.hashCode(geoLocation);
        h += (h << 5) + Objects.hashCode(startPage);
        h += (h << 5) + Objects.hashCode(startIndex);
        h += (h << 5) + Objects.hashCode(safeSearch);
        h += (h << 5) + Objects.hashCode(additionalParams);
        return h;
    }

    @Override
    public String toString() {
        return "WebSearchRequest{" +
                "searchTerms='" + searchTerms + '\'' +
                ", maxResults=" + maxResults +
                ", language='" + language + '\'' +
                ", geoLocation='" + geoLocation + '\'' +
                ", startPage=" + startPage +
                ", startIndex=" + startIndex +
                ", siteRestrict=" + safeSearch +
                ", additionalParams=" + additionalParams +
                '}';
    }

    /**
     * Create a new builder instance.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String searchTerms;
        private Integer maxResults;
        private String language;
        private String geoLocation;
        private Integer startPage;
        private Integer startIndex;
        private Boolean safeSearch;
        private Map<String, Object> additionalParams;

        private Builder() {
        }

        /**
         * Set the search terms.
         *
         * @param searchTerms The keyword or keywords desired by the search user.
         * @return The builder instance.
         */
        public Builder searchTerms(String searchTerms) {
            this.searchTerms = searchTerms;
            return this;
        }

        /**
         * Set the maximum number of results.
         *
         * @param maxResults The maximum number of results.
         * @return The builder instance.
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Set the desired language for search results.
         *
         * @param language The desired language for search results.
         * @return The builder instance.
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * Set the desired geolocation for search results.
         *
         * @param geoLocation The desired geolocation for search results.
         * @return The builder instance.
         */
        public Builder geoLocation(String geoLocation) {
            this.geoLocation = geoLocation;
            return this;
        }

        /**
         * Set the start page number for search results.
         *
         * @param startPage The start page number for search results.
         * @return The builder instance.
         */
        public Builder startPage(Integer startPage) {
            this.startPage = startPage;
            return this;
        }

        /**
         * Set the start index for search results.
         *
         * @param startIndex The start index for search results.
         * @return The builder instance.
         */
        public Builder startIndex(Integer startIndex) {
            this.startIndex = startIndex;
            return this;
        }

        /**
         * Set the safe search flag.
         *
         * @param safeSearch The safe search flag.
         * @return The builder instance.
         */
        public Builder safeSearch(Boolean safeSearch) {
            this.safeSearch = safeSearch;
            return this;
        }

        /**
         * Set the additional parameters for the search request.
         *
         * @param additionalParams The additional parameters for the search request.
         * @return The builder instance.
         */
        public Builder additionalParams(Map<String, Object> additionalParams) {
            this.additionalParams = additionalParams;
            return this;
        }

        /**
         * Build the web search request.
         *
         * @return The web search request.
         */
        public WebSearchRequest build() {
            return new WebSearchRequest(this);
        }
    }

    /**
     * Create a web search request with the given search terms.
     *
     * @param searchTerms The search terms.
     * @return The web search request.
     */
    public static WebSearchRequest from(String searchTerms) {
        return WebSearchRequest.builder().searchTerms(searchTerms).build();
    }

    /**
     * Create a web search request with the given search terms and maximum number of results.
     *
     * @param searchTerms The search terms.
     * @param maxResults The maximum number of results.
     * @return The web search request.
     */
    public static WebSearchRequest from(String searchTerms, Integer maxResults) {
        return WebSearchRequest.builder().searchTerms(searchTerms).maxResults(maxResults).build();
    }
}
