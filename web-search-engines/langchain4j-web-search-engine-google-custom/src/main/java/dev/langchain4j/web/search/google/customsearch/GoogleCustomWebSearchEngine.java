package dev.langchain4j.web.search.google.customsearch;

import com.google.api.client.json.GenericJson;
import com.google.api.services.customsearch.v1.model.Result;
import com.google.api.services.customsearch.v1.model.Search;
import dev.langchain4j.web.search.*;
import lombok.Builder;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.api.services.customsearch.v1.model.Search.Queries;
import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of a {@link WebSearchEngine} that uses
 * <a href="https://programmablesearchengine.google.com/">Google Custom Search API</a> for performing web searches.
 */
public class GoogleCustomWebSearchEngine implements WebSearchEngine {

    private final GoogleCustomSearchApiClient googleCustomSearchApiClient;
    private final Boolean includeImages;

    /**
     * Constructs a new GoogleCustomWebSearchEngine with the specified parameters.
     *
     * @param apiKey        the Google Search API key for accessing the Google Custom Search API
     *                      <p>
     *                      You can just generate an API key <a href="https://developers.google.com/custom-search/docs/paid_element#api_key">here</a>
     * @param csi           the Custom Search ID parameter for search the entire web
     *                      <p>
     *                      You can create a Custom Search Engine <a href="https://cse.google.com/cse/create/new">here</a>
     * @param siteRestrict  if your Search Engine is restricted to only searching specific sites, you can set this parameter to true.
     *                      <p>
     *                      Default value is false. View the documentation for more information <a href="https://developers.google.com/custom-search/v1/site_restricted_api">here</a>
     * @param includeImages If it is true then include public images relevant to the query. This can add more latency to the search.
     *                      <p>
     *                      Default value is false.
     * @param timeout       the timeout duration for API requests
     *                      <p>
     *                      Default value is 60 seconds.
     * @param maxRetries    the maximum number of retries for API requests
     *                      <p>
     *                      Default value is 10.
     * @param logRequests   whether to log API requests
     *                      <p>
     *                      Default value is false.
     * @param logResponses  whether to log API responses
     *                      <p>
     *                      Default value is false.
     */
    @Builder
    public GoogleCustomWebSearchEngine(String apiKey,
                                       String csi,
                                       Boolean siteRestrict,
                                       Boolean includeImages,
                                       Duration timeout,
                                       Integer maxRetries,
                                       Boolean logRequests,
                                       Boolean logResponses) {

        this.googleCustomSearchApiClient = GoogleCustomSearchApiClient.builder()
                .apiKey(apiKey)
                .csi(csi)
                .siteRestrict(getOrDefault(siteRestrict, false))
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .maxRetries(getOrDefault(maxRetries, 3))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.includeImages = getOrDefault(includeImages, false);
    }

    /**
     * Creates a new builder for constructing a GoogleCustomWebSearchEngine with the specified API key and Custom Search ID.
     *
     * @param apiKey the API key for accessing the Google Custom Search API
     * @param csi    the Custom Search ID parameter for search the entire web
     * @return a new builder instance
     */
    public static GoogleCustomWebSearchEngine withApiKeyAndCsi(String apiKey, String csi) {
        return GoogleCustomWebSearchEngine.builder().apiKey(apiKey).csi(csi).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        ensureNotNull(webSearchRequest, "webSearchRequest");

        Queries.Request requestQuery = new Queries.Request();
        requestQuery.setSearchTerms(webSearchRequest.searchTerms());
        requestQuery.setCount(getOrDefault(webSearchRequest.maxResults(), 5));
        requestQuery.setGl(webSearchRequest.geoLocation());
        requestQuery.setLanguage(webSearchRequest.language());
        requestQuery.setStartPage(webSearchRequest.startPage());
        requestQuery.setStartIndex(webSearchRequest.startIndex());
        requestQuery.setSafe(webSearchRequest.safeSearch() ? "active" : "off");
        requestQuery.setFilter("1"); // By default, applies filtering to remove duplicate content
        requestQuery.setCr(setCountryRestrict(webSearchRequest));
        webSearchRequest.additionalParams().forEach(requestQuery::set);

        boolean searchTypeImage = "image".equals(requestQuery.getSearchType());

        // Web search
        Search search = googleCustomSearchApiClient.searchResults(requestQuery);
        Map<String, Object> searchMetadata = toSearchMetadata(search, searchTypeImage);
        Map<String, Object> searchInformationMetadata = new HashMap<>();

        // Images search
        if (includeImages && !searchTypeImage) {
            requestQuery.setSearchType("image");
            Search imagesSearch = googleCustomSearchApiClient.searchResults(requestQuery);
            if (!isNullOrEmpty(imagesSearch.getItems())){
                List<ImageSearchResult> images = imagesSearch.getItems().stream()
                        .map(result -> ImageSearchResult.from(
                                result.getTitle(),
                                URI.create(result.getLink()),
                                URI.create(result.getImage().getContextLink()),
                                URI.create(result.getImage().getThumbnailLink())))
                        .collect(toList());
                addImagesToSearchInformation(searchInformationMetadata, images);
            }
        }

        return WebSearchResults.from(
                searchMetadata,
                WebSearchInformationResult.from(
                        Long.valueOf(getOrDefault(search.getSearchInformation().getTotalResults(), "0")),
                        !isNullOrEmpty(search.getQueries().getRequest())
                                ? calculatePageNumberFromQueries(search.getQueries().getRequest().get(0)) : 1,
                        searchInformationMetadata.isEmpty() ? null : searchInformationMetadata),
                toWebSearchOrganicResults(search, searchTypeImage));
    }

    private static void addImagesToSearchInformation(Map<String, Object> searchInformationMetadata, List<ImageSearchResult> images) {
        if (!isNullOrEmpty(images)) {
            searchInformationMetadata.put("images", images);
        }
    }

    private static Map<String, Object> toSearchMetadata(Search search, Boolean searchTypeImage) {
        if (search == null) {
            return null;
        }
        Map<String, Object> searchMetadata = new HashMap<>();
        searchMetadata.put("status", "Success");
        searchMetadata.put("searchTime", search.getSearchInformation().getSearchTime());
        searchMetadata.put("processedAt", LocalDateTime.now().toString());
        searchMetadata.put("searchType", searchTypeImage ? "images" : "web");
        searchMetadata.putAll(search.getContext());
        return searchMetadata;
    }

    private static Map<String, String> toResultMetadataMap(Result result, boolean searchTypeImage) {
        Map<String, String> metadata = new HashMap<>();
        // Image search type
        if (searchTypeImage) {
            metadata.put("imageLink", result.getLink());
            metadata.put("contextLink", result.getImage().getContextLink());
            metadata.put("thumbnailLink", result.getImage().getThumbnailLink());
            metadata.put("mimeType", result.getMime());
            return metadata;
        }
        // Web search type
        if (!result.getPagemap().isEmpty()) {
            result.getPagemap().forEach((key, value) -> {
                if (key.equals("metatags")) {
                    if (value instanceof List) {
                        metadata.put(key, ((List<?>) value).stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse(""));
                    } else {
                        metadata.put(key, value.toString());
                    }
                }
                metadata.put("mimeType", isNotNullOrBlank(result.getMime()) ? result.getMime() : "text/html");
            });
            return metadata;
        }
        return null;
    }

    private static List<WebSearchOrganicResult> toWebSearchOrganicResults(Search search, Boolean searchTypeImage) {
        List<WebSearchOrganicResult>  organicResults = new ArrayList<>();
        if (!isNullOrEmpty(search.getItems())) {
            organicResults = search.getItems().stream()
                    .map(result -> WebSearchOrganicResult.from(
                            result.getTitle(),
                            URI.create(result.getLink()),
                            result.getSnippet(),
                            null, // by default google custom search api does not return content
                            toResultMetadataMap(result, searchTypeImage)
                    )).collect(toList());
        }
        return organicResults;
    }

    private static Integer calculatePageNumberFromQueries(GenericJson query) {
        if (query instanceof Queries.PreviousPage) {
            Queries.PreviousPage previousPage = (Queries.PreviousPage) query;
            return calculatePageNumber(previousPage.getStartIndex());
        }
        if (query instanceof Queries.Request) {
            Queries.Request currentPage = (Queries.Request) query;
            return calculatePageNumber(getOrDefault(currentPage.getStartIndex(), 1));
        }
        if (query instanceof Queries.NextPage) {
            Queries.NextPage nextPage = (Queries.NextPage) query;
            return calculatePageNumber(nextPage.getStartIndex());
        }
        return null;
    }

    private static Integer calculatePageNumber(Integer startIndex) {
        if (startIndex == null)
            return null;
        return ((startIndex - 1) / 10) + 1;
    }

    private static String setCountryRestrict(WebSearchRequest webSearchRequest) {
        return webSearchRequest.additionalParams().get("cr") != null ? webSearchRequest.additionalParams().get("cr").toString()
                : isNotNullOrBlank(webSearchRequest.geoLocation()) ? "country" + webSearchRequest.geoLocation().toUpperCase()
                : ""; // default value
    }

    public static final class ImageSearchResult {
        private final String title;
        private final URI imageLink;
        private final URI contextLink;
        private final URI thumbnailLink;

        private ImageSearchResult(String title, URI imageLink) {
            this.title = ensureNotNull(title, "title");
            this.imageLink = ensureNotNull(imageLink, "imageLink");
            this.contextLink = null;
            this.thumbnailLink = null;
        }

        private ImageSearchResult(String title, URI imageLink, URI contextLink, URI thumbnailLink) {
            this.title = ensureNotNull(title, "title");
            this.imageLink = ensureNotNull(imageLink, "imageLink");
            this.contextLink = contextLink;
            this.thumbnailLink = thumbnailLink;
        }

        public String title() {
            return title;
        }

        public URI imageLink() {
            return imageLink;
        }

        public URI contextLink() {
            return contextLink;
        }

        public URI thumbnailLink() {
            return thumbnailLink;
        }

        @Override
        public String toString() {
            return "ImageSearchResult{" +
                    "title='" + title + '\'' +
                    ", imageLink=" + imageLink +
                    ", contextLink=" + contextLink +
                    ", thumbnailLink=" + thumbnailLink +
                    '}';
        }

        public static ImageSearchResult from(String title, URI imageLink) {
            return new ImageSearchResult(title, imageLink);
        }

        public static ImageSearchResult from(String title, URI imageLink, URI contextLink, URI thumbnailLink) {
            return new ImageSearchResult(title, imageLink, contextLink, thumbnailLink);
        }
    }
}
