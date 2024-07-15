package dev.langchain4j.web.search.searchapi;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
class SearchApiWebSearchResponse {

    private List<OrganicResult> organicResults;

    /**
     * Always present in a successful response
     */
    private SearchParameters searchParameters;

    /**
     * May or may not be present
     */
    private Pagination pagination;

    /**
     * May or may not be present
     */
    private SearchInformation searchInformation;

}

@Getter
@Builder
class OrganicResult {

    private String title;
    private String link;
    private String snippet;
}

@Getter
@Builder
class Pagination {

    private Integer current;
    private String next;
}

@Getter
@Builder
class SearchParameters {

    private String engine;
    private String q;
}

@Getter
@Builder
class SearchInformation {

    private Long totalResults;
}

