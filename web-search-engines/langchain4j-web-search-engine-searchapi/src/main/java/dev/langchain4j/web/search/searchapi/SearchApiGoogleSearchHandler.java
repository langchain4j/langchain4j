package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class SearchApiGoogleSearchHandler implements SearchApiRequestResponseHandler {

    @Override
    public Map<String, Object> getAdditionalParams(WebSearchRequest webSearchRequest) {
        Integer maxResults = webSearchRequest.maxResults();
        String language = webSearchRequest.language();
        String geoLocation = webSearchRequest.geoLocation();
        Integer startPage = webSearchRequest.startPage();
        Boolean safeSearch = webSearchRequest.safeSearch();
        Map<String, Object> searchApiParams = new HashMap<>(webSearchRequest.additionalParams());
        if (geoLocation != null) {
            searchApiParams.put("gl", geoLocation);
        }
        if (language != null) {
            searchApiParams.put("lr", language);
        }
        if (startPage != null) {
            searchApiParams.put("page", startPage);
        }
        if (maxResults != null) {
            searchApiParams.put("num", maxResults);
        } else if (!searchApiParams.containsKey("num")) {
            searchApiParams.put("num", 5); // default value
        }
        if (safeSearch != null) {
            String safe = safeSearch ? "active" : "off";
            searchApiParams.put("safe", safe);
        }
        return searchApiParams;
    }

    @Override
    public WebSearchResults getWebSearchResult(SearchApiResponse response) {
        if (!(response instanceof SearchApiGoogleSearchResponse)) {
            throw new RuntimeException("Response class not suitable for this handler");
        }
        SearchApiGoogleSearchResponse googleSearchResponse = (SearchApiGoogleSearchResponse) response;
        List<WebSearchOrganicResult> results = googleSearchResponse.getOrganicResults()
                .stream()
                .map(result -> WebSearchOrganicResult.from(
                        result.getTitle(),
                        URI.create(result.getLink()),
                        result.getSnippet(),
                        null,  // by default google custom search api does not return content
                        toResultMetadata(result) // TODO: not sure which information goes here
                ))
                .collect(Collectors.toList());
        Long totalResults = googleSearchResponse.getSearchInformation().getTotalResults();
        WebSearchInformationResult searchInformation = WebSearchInformationResult.from(totalResults,
                googleSearchResponse.getPagination().getCurrent(),
                googleSearchResponse.getSearchParameters() // TODO: not sure which information goes here
        );
        return WebSearchResults.from(googleSearchResponse.getSearchMetadata(),
                searchInformation,
                results);
    }

    private Map<String, String> toResultMetadata(SearchResult result) {
        Map<String, AboutResult> aboutThisResult = result.getAboutThisResult();
        if (aboutThisResult != null && aboutThisResult.containsKey("source")) {
            AboutResult source = aboutThisResult.get("source");
            return source.toMetadata();
        } else {
            return null;
        }
    }
}
