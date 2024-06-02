package dev.langchain4j.web.search.searchapi;

import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

import java.util.Map;

interface SearchApiRequestResponseHandler {

    Map<String, Object> getAdditionalParams(WebSearchRequest webSearchRequest);

    WebSearchResults getWebSearchResult(SearchApiResponse response);
}
