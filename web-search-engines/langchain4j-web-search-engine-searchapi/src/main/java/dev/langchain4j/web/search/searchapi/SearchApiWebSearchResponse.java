package dev.langchain4j.web.search.searchapi;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
class SearchApiWebSearchResponse {

    private List<OrganicResult> organicResults;

    /**
     * Always present in a successful response
     */
    private Map<String, Object> searchParameters;

    /**
     * May or may not be present
     */
    private Map<String, Object> pagination;

}

@Getter
@Builder
class OrganicResult {

    private String title;
    private String link;

    /**
     * May or may not be present
     */
    private String snippet;
}